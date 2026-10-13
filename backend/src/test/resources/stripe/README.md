Recorded Stripe API answers for `StripeClientTest` (playback testing, as Stripe recommends next to
stripe-mock, which cannot return declines). Shaped after the responses Stripe documents for its test cards
(https://docs.stripe.com/testing, https://docs.stripe.com/error-codes). Trimmed to the fields the shop reads
plus a few it ignores, so a test also proves unknown fields do not break the parser.
Re-record from test mode with `StripeSandboxIntegrationTest` when Stripe changes its answers.
