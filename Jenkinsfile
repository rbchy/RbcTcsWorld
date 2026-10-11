// Jenkins pipeline with the same quality gates as .github/workflows/ci.yml.
// Jenkins runs on 8080, which is why the backend uses 8081.
//
// Agent needs: JDK 21, Maven, Docker with compose v2, python3, jq, Google Chrome (UI tests), curl.
// k6 is used when installed (brew install k6); otherwise the grafana/k6 image is used.
// Plugins: Pipeline, Git, JUnit, Timestamper (all in Jenkins' "suggested plugins") + HTML Publisher
// (optional: the "QA Reports" page with every report of the build; without it the reports are only archived).
// The syntax of this file is validated in GitHub Actions by a real Jenkins (job "Jenkinsfile lint").

pipeline {
    agent any

    // Same JDK as GitHub Actions (Temurin 21). "jdk-21" is a JDK installation in Manage Jenkins > Tools
    // (JAVA_HOME = output of /usr/libexec/java_home -v 21). Without it, Jenkins used whatever "java" came
    // first on the PATH: Java 26 in one build, Java 23 in the next.
    tools { jdk 'jdk-21' }

    options {
        timestamps()
        timeout(time: 120, unit: 'MINUTES')            // safety net only - every stage has its own, tighter limit
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()                       // one stack per agent: fixed ports 5432/8081/5173
    }

    parameters {
        booleanParam(name: 'RUN_UI', defaultValue: true, description: 'Selenium UI tests')
        choice(name: 'BROWSER', choices: ['chrome', 'firefox', 'edge', 'safari'], description: 'Browser for the UI and accessibility tests (must be installed on the agent; Safari: run "sudo safaridriver --enable" once)')
        booleanParam(name: 'RUN_MUTATION', defaultValue: true, description: 'PIT mutation testing of the business logic (about 4 minutes)')
        booleanParam(name: 'RUN_SECURITY', defaultValue: true, description: 'OSV dependency scan + API contract gate + OWASP ZAP API scan')
        choice(name: 'K6_EXTRA', choices: ['none', 'load', 'stress', 'spike', 'soak'], description: 'Extra (long) k6 test after the gates')
    }

    environment {
        // macOS agents: Homebrew (mvn, k6, jq) and Docker Desktop's CLI, which lives in ~/.docker/bin when
        // Docker Desktop was installed "per user" - Jenkins does not read the login shell's PATH.
        PATH = "/opt/homebrew/bin:/usr/local/bin:${env.HOME}/.docker/bin:/Applications/Docker.app/Contents/Resources/bin:${env.PATH}"
        BASE_URL = 'http://localhost:8081'
        UI_URL = 'http://localhost:5173'
        COMPOSE = 'docker compose --profile app'
        NETWORK = 'rbctcsworld_default'                       // compose network: containers reach "backend:8081"
    }

    stages {
        stage('Tools on the agent') {
            options { timeout(time: 3, unit: 'MINUTES') }
            steps {
                // fail in seconds with a clear message instead of in the middle of the pipeline
                // reports of an earlier build must never show up as this build's result (build #30)
                sh 'rm -rf qa-reports backend/target automation/target performance/reports osv-results.json security/zap/zap-report.*'
                sh '''
                    for t in java mvn docker python3 jq curl; do
                      command -v "$t" >/dev/null || { echo "MISSING TOOL: $t (PATH=$PATH)"; exit 1; }
                    done
                    # Maven uses JAVA_HOME (set by "tools { jdk 'jdk-21' }"), not the first "java" on the PATH
                    # (Homebrew may put another JDK there; the PATH set in "environment" also hides the tool's bin)
                    echo "JAVA_HOME=$JAVA_HOME"
                    "$JAVA_HOME/bin/java" -version 2>&1 | head -1
                    "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -q '"21' || {
                      echo "WRONG JAVA: JDK installation 'jdk-21' points to $JAVA_HOME, which is not Java 21. Manage Jenkins > Tools > JDK installations > jdk-21: JAVA_HOME = output of '/usr/libexec/java_home -v 21' (install: brew install --cask temurin@21)"
                      exit 1
                    }
                    mvn -v | grep -i '^Java version'
                    # "docker version" waits forever when Docker Desktop hangs (build #19 sat here for 46 minutes):
                    # give it 60 seconds, then fail with a message that says what to do
                    docker version --format 'Docker {{.Server.Version}}' > docker-version.txt 2>&1 &
                    pid=$!
                    for i in $(seq 1 60); do kill -0 $pid 2>/dev/null || break; sleep 1; done
                    if kill -0 $pid 2>/dev/null; then
                      kill $pid
                      echo "DOCKER DOES NOT ANSWER within 60 s - restart Docker Desktop (Quit + open), check 'docker version' in Terminal, run the build again"
                      exit 1
                    fi
                    wait $pid || { cat docker-version.txt; echo "Docker Desktop is not running - start it and run the build again"; exit 1; }
                    cat docker-version.txt
                    docker compose version
                '''
                // the chosen browser must be able to start - otherwise every UI test waits ~40 s for it (build #21:
                // BROWSER=safari without "Allow remote automation" = 20 errors after 12 minutes)
                sh '''
                    [ "${RUN_UI:-true}" = "true" ] || exit 0
                    case "${BROWSER:-chrome}" in
                      chrome)  app="Google Chrome";  cask=google-chrome ;;
                      firefox) app="Firefox";        cask=firefox ;;
                      edge)    app="Microsoft Edge"; cask=microsoft-edge ;;
                      safari)  app="Safari";         cask="" ;;
                    esac
                    if [ "$(uname)" = "Darwin" ] && [ ! -d "/Applications/$app.app" ] && [ ! -d "$HOME/Applications/$app.app" ]; then
                      echo "BROWSER NOT INSTALLED: $app.app is not in /Applications or ~/Applications - install it (brew install --cask $cask) or choose another BROWSER"
                      exit 1
                    fi
                    if [ "${BROWSER:-chrome}" = "safari" ]; then
                      safaridriver -p 4445 > safaridriver.log 2>&1 &
                      sd=$!
                      sleep 2
                      resp=$(curl -s -m 20 -X POST http://localhost:4445/session -H 'Content-Type: application/json' \\
                             -d '{"capabilities":{"alwaysMatch":{"browserName":"safari"}}}' || true)
                      sid=$(printf '%s' "$resp" | jq -r '.value.sessionId // empty' 2>/dev/null || true)
                      [ -n "$sid" ] && curl -s -m 10 -X DELETE "http://localhost:4445/session/$sid" > /dev/null || true
                      kill $sd 2>/dev/null || true
                      if [ -z "$sid" ]; then
                        echo "SAFARI NOT READY: Safari > Settings > Advanced > 'Show features for web developers', then Developer > 'Allow remote automation'; run 'sudo safaridriver --enable' once; keep the Mac unlocked during the build"
                        printf '%s\\n' "$resp" | head -c 400; echo
                        exit 1
                      fi
                      echo "Safari: WebDriver session opened and closed - ready"
                    fi
                    echo "Browser for UI tests: ${BROWSER:-chrome} ($app)"
                '''
            }
        }

        stage('Traceability matrix') {
            options { timeout(time: 2, unit: 'MINUTES') }
            steps { sh 'python3 docs/qa/check_rtm.py' }
        }

        stage('Backend: unit + integration + coverage gate') {
            options { timeout(time: 20, unit: 'MINUTES') }
            steps { sh 'mvn -B -f backend/pom.xml clean verify' }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                    sh '.github/scripts/coverage-summary.sh backend/target/site/jacoco/jacoco.csv || true'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'backend/target/site/jacoco/**'
                }
            }
        }

        stage('Mutation testing (PIT)') {
            options { timeout(time: 20, unit: 'MINUTES') }
            when { expression { params.RUN_MUTATION } }
            steps {
                sh 'mvn -B -f backend/pom.xml -Pmutation -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage'
            }
            post {
                always {
                    sh '.github/scripts/mutation-summary.sh backend/target/pit-reports/mutations.xml 0 || true'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'backend/target/pit-reports/**'
                }
            }
        }

        stage('Start the whole app in Docker') {
            options { timeout(time: 15, unit: 'MINUTES') }
            steps {
                sh "${COMPOSE} down -v --remove-orphans || true"
                sh "${COMPOSE} up -d --build --wait --wait-timeout 300"
                sh "${COMPOSE} ps"
            }
        }

        stage('Automation: API + DB + BDD + UI') {
            options { timeout(time: 30, unit: 'MINUTES') }
            steps {
                script {
                    def filter = params.RUN_UI ? '' : '-DexcludedGroups=ui'
                    sh "mvn -B -f automation/pom.xml clean test ${filter} -DbaseUrl=${BASE_URL} -DuiUrl=${UI_URL} -Dheadless=true -Dbrowser=${params.BROWSER}"
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'automation/target/surefire-reports/*.xml'
                    sh 'mvn -B -q -f automation/pom.xml allure:report || true'
                    archiveArtifacts allowEmptyArchive: true,
                            artifacts: 'automation/target/allure-report/**, automation/target/cucumber-report.html'
                }
            }
        }

        stage('Performance gates (k6)') {
            options { timeout(time: 10, unit: 'MINUTES') }
            steps {
                sh '''
                    performance/k6run.sh -e DURATION=30s performance/tests/smoke.js
                    performance/k6run.sh -e BUYERS=60 -e STOCK=15 performance/tests/flash-sale.js
                    performance/k6run.sh -e DURATION=20s performance/tests/catalog.js
                '''
            }
            post {
                always { archiveArtifacts allowEmptyArchive: true, artifacts: 'performance/reports/**' }
            }
        }

        stage('Security: dependencies + API contract + OWASP ZAP') {
            options { timeout(time: 30, unit: 'MINUTES') }
            when { expression { params.RUN_SECURITY } }
            steps {
                sh '''
                    # SBOM (Maven) + OSV-Scanner with completeness check and self-test (DEF-021);
                    # exceptions: backend/osv-scanner.toml
                    security/osv-scan.sh
                    security/osv-summary.sh osv-results.json

                    # API contract (provider side): no breaking change against the approved OpenAPI baseline
                    curl -fsS "$BASE_URL/v3/api-docs" -o security/zap/openapi.json
                    .github/scripts/openapi-breaking-changes.sh docs/api/openapi.json security/zap/openapi.json

                    # OWASP ZAP API scan from the OpenAPI spec, logged in as a customer, inside the compose network
                    TOKEN=$(curl -fsS -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
                        -d "{\\"email\\":\\"zap-$BUILD_NUMBER@security.test\\",\\"password\\":\\"Password1!\\"}" | jq -r .token)
                    chmod -R 777 security/zap
                    set +e
                    docker run --rm --network "$NETWORK" -v "$PWD/security/zap:/zap/wrk:rw" \
                        -e ZAP_AUTH_HEADER=Authorization -e ZAP_AUTH_HEADER_VALUE="Bearer $TOKEN" \
                        ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
                        -t http://backend:8081/v3/api-docs -f openapi -O http://backend:8081 \
                        -c zap-rules.tsv -I -r zap-report.html -J zap-report.json
                    code=$?
                    set -e
                    security/zap/summary.sh security/zap/zap-report.json
                    if [ "$code" -eq 1 ] || [ "$code" -eq 3 ]; then echo "OWASP ZAP failed (exit $code)"; exit 1; fi
                '''
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true,
                            artifacts: 'osv-results.json, security/zap/zap-report.html, security/zap/zap-report.json'
                }
            }
        }

        stage('Extra k6 test') {
            options { timeout(time: 60, unit: 'MINUTES') }
            when { expression { params.K6_EXTRA != 'none' } }
            steps {
                sh "performance/k6run.sh performance/tests/${params.K6_EXTRA}.js"
            }
            post {
                always { archiveArtifacts allowEmptyArchive: true, artifacts: 'performance/reports/**' }
            }
        }
    }

    post {
        failure {
            sh "${COMPOSE} logs --no-color --tail=100 || true"
        }
        always {
            // ONE page with every report of this build: numbers of all gates + links to Allure, Cucumber, JaCoCo,
            // PIT, k6 and ZAP. Shown as "QA Reports" on the build page (HTML Publisher plugin).
            sh "PIPELINE_RESULT=${currentBuild.currentResult} python3 ci/qa_dashboard.py qa-reports || true"
            archiveArtifacts allowEmptyArchive: true, artifacts: 'qa-reports/**'
            script {
                try {
                    publishHTML(target: [reportDir: 'qa-reports', reportFiles: 'index.html', reportName: 'QA Reports',
                                         keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true])
                } catch (NoSuchMethodError ignored) {
                    echo 'Install the "HTML Publisher" plugin to get the QA Reports page (reports are archived anyway).'
                }
            }
            sh "${COMPOSE} down -v --remove-orphans || true"
        }
        success {
            echo 'All quality gates passed: tests, coverage, traceability, performance and security.'
        }
    }
}
