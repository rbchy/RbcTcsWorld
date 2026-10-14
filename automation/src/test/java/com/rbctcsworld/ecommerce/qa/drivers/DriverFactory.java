package com.rbctcsworld.ecommerce.qa.drivers;

import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the browser from -Dbrowser=chrome|firefox|edge|safari|android-chrome|ios-safari and -Dheadless=true|false.
 * Desktop: Selenium Manager downloads the drivers. Phones: an Appium server (-DappiumUrl) drives Chrome on an
 * Android emulator/device or Safari on an iOS Simulator/iPhone. Appium speaks the same W3C WebDriver protocol,
 * so a plain RemoteWebDriver with Appium capabilities is enough for mobile web, and every page object works
 * unchanged on the phone (no java-client, no second Selenium version on the class path).
 */
public final class DriverFactory {

    /*
     * Selenium warns "Unable to find CDP implementation matching 154" when Chrome is newer than the
     * DevTools bindings bundled with this Selenium version. The tests do not use DevTools (CDP), so the
     * warning is noise. Strong references keep the JUL loggers (and their level) from being garbage collected.
     */
    private static final Logger CDP_FINDER = Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder");
    private static final Logger CHROMIUM = Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver");

    static {
        CDP_FINDER.setLevel(Level.SEVERE);
        CHROMIUM.setLevel(Level.SEVERE);
    }

    private DriverFactory() {
    }

    /*
     * Same viewport (1366x900) in every browser, set by a START argument: resizing a running headless Edge window
     * timed out once in CI (setCurrentWindowSize), so a resize after start is used only for Safari.
     */
    public static WebDriver create() {
        boolean headless = TestConfig.headless();
        return switch (TestConfig.browser().toLowerCase()) {
            case "firefox" -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) o.addArguments("-headless");
                o.addArguments("--width=1366", "--height=900");
                yield new FirefoxDriver(o);
            }
            case "android-chrome", "ios-safari" -> mobile(TestConfig.browser().toLowerCase());
            // Safari (macOS only): no headless mode; enable once with "sudo safaridriver --enable"
            case "safari" -> {
                WebDriver safari = new org.openqa.selenium.safari.SafariDriver();
                safari.manage().window().setSize(new org.openqa.selenium.Dimension(1366, 900));
                yield safari;
            }
            case "edge" -> {
                EdgeOptions o = new EdgeOptions();
                if (headless) o.addArguments("--headless=new");
                o.addArguments("--window-size=1366,900");
                // DEF-019: on the Ubuntu 24.04 CI runners AppArmor blocks Edge's sandbox (Chrome ships an AppArmor
                // profile, Edge does not), so Edge exits at start. Only in CI, never on a developer machine.
                if (System.getenv("CI") != null) o.addArguments("--no-sandbox", "--disable-dev-shm-usage");
                yield new EdgeDriver(o);
            }
            default -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) o.addArguments("--headless=new");
                o.addArguments("--window-size=1366,900");
                yield new ChromeDriver(o);
            }
        };
    }

    public static boolean isMobile() {
        return TestConfig.browser().toLowerCase().matches("android-chrome|ios-safari");
    }

    /** W3C capabilities for Appium 3 (UiAutomator2 driver for Android, XCUITest driver for iOS). */
    static MutableCapabilities mobileCapabilities(String browser, String deviceId) {
        MutableCapabilities caps = new MutableCapabilities();
        caps.setCapability("appium:newCommandTimeout", 180);
        if (!deviceId.isBlank()) caps.setCapability("appium:udid", deviceId);
        if (browser.equals("android-chrome")) {
            caps.setCapability("platformName", "Android");
            caps.setCapability("browserName", "Chrome");
            caps.setCapability("appium:automationName", "UiAutomator2");
            // Appium fetches the chromedriver that matches the phone's Chrome (server flag
            // --allow-insecure=*:chromedriver_autodownload)
            caps.setCapability("appium:chromedriverAutodownload", true);
            caps.setCapability("appium:uiautomator2ServerInstallTimeout", 120_000);
            caps.setCapability("appium:adbExecTimeout", 120_000);
            // no "welcome to Chrome" screens in front of the shop
            caps.setCapability("goog:chromeOptions", Map.of("args", List.of("--disable-fre", "--no-first-run",
                    "--no-default-browser-check", "--disable-notifications")));
        } else {
            caps.setCapability("platformName", "iOS");
            caps.setCapability("browserName", "Safari");
            caps.setCapability("appium:automationName", "XCUITest");
            // the first session builds WebDriverAgent on the Mac: minutes, not seconds
            caps.setCapability("appium:wdaLaunchTimeout", 300_000);
            caps.setCapability("appium:wdaConnectionTimeout", 300_000);
            caps.setCapability("appium:safariIgnoreFraudWarning", true);
            caps.setCapability("appium:simulatorStartupTimeout", 300_000);   // a busy CI Mac boots slowly
            // Safari's remote debugger needs a moment after launch on a busy CI Mac (default 5 s was not enough)
            caps.setCapability("appium:webviewConnectTimeout", 30_000);
        }
        return caps;
    }

    /**
     * A phone session that has really loaded the shop once. On the CI emulator Chrome sometimes loses its DevTools
     * connection right after start ("disconnected: not connected to DevTools"); such a session is dead, so it is
     * replaced by a fresh one (at most 3 tries) instead of failing the test that happens to get it.
     */
    private static WebDriver mobile(String browser) {
        org.openqa.selenium.WebDriverException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebDriver driver = newMobileSession(browser);
            try {
                driver.get(TestConfig.uiUrl() + "/#/");
                return driver;
            } catch (org.openqa.selenium.WebDriverException e) {
                last = e;
                System.err.println("Phone session " + attempt + " could not load the shop, starting a new one: "
                        + e.getMessage().lines().findFirst().orElse(""));
                try {
                    driver.quit();
                } catch (RuntimeException ignored) {
                    // the session is already broken
                }
            }
        }
        throw last;
    }

    private static WebDriver newMobileSession(String browser) {
        try {
            // creating a session can take minutes (WebDriverAgent build/launch on iOS, chromedriver download on
            // Android): wait longer than Selenium's default of 3 minutes for that answer
            WebDriver driver = RemoteWebDriver.builder()
                    .oneOf(mobileCapabilities(browser, TestConfig.deviceId()))
                    .address(URI.create(TestConfig.appiumUrl()).toURL())
                    .config(ClientConfig.defaultConfig().readTimeout(Duration.ofMinutes(8)))
                    .build();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            return driver;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("appiumUrl is not a URL: " + TestConfig.appiumUrl(), e);
        }
    }
}
