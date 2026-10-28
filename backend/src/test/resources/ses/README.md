# Test-only SNS signing material

`sns-test-key.pem` / `sns-test-cert.pem` are a **self-signed, test-only** RSA key pair made with openssl for these tests.
Nothing trusts this certificate; it only lets the tests sign SNS messages the way Amazon SNS does.
The `sns_*.json` messages were signed outside Java (openssl dgst -sha256 / -sha1 over the canonical SNS string),
so `SnsMessageVerifierTest` checks the Java verifier against an independent signer.
