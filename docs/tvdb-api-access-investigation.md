# TVDB API access investigation

Date: 2026-07-28

## Context

The TVDB dashboard currently shows the following information for Episort:

- Project name: `Episort`
- Funding model: `Negotiated Contract`
- Status: `Inactive`

The API key nevertheless appeared to work during an initial Episort test. A
clean authentication test still needs to be performed.

## Current interpretation

The `Inactive` label is most likely an administrative or contractual status,
not proof that authentication has already been technically disabled.

TVDB documents two access models:

1. A licensed or negotiated-contract project.
2. A user-supported project where every user supplies a personal subscriber
   PIN.

For a negotiated contract, TVDB states that the key remains inactive while the
project is waiting for manual review and approval. A project below the current
revenue threshold may qualify for a zero-fee tier, but it still needs approval.
The free tier also requires attribution with a direct link to TheTVDB.

This model is similar to FileBot's documented approach:

- FileBot users do not enter personal TVDB API keys.
- FileBot's developer stated that its project key was built into the
  application.
- FileBot's developer also stated that FileBot has held a commercial TVDB
  licence since March 2020.
- FileBot 5.x logs show direct calls to the TVDB v4 API.

FileBot's precise current key-distribution mechanism and negotiated terms are
not public. Its important protection is therefore the explicit agreement with
TVDB, not an assumption that a desktop-embedded credential can remain secret.

## Episort credential exposure

The previous Episort API key must be considered compromised independently of
the dashboard status:

- Commit `9712aa7` embedded the key using reversible XOR obfuscation.
- Commit `bfda5e5` contained a non-placeholder, 36-character credential in
  plaintext.
- Both commits remain in the ancestry of `origin/develop`.

Removing the credential from the current source does not remove it from Git
history, old clones, or distributed binaries. The old key should not be reused
after TVDB approves the project.

The current source loads the project key from the `TVDB_API_KEY` environment
variable rather than embedding it in the repository.

## Authentication test still required

The decisive test is a fresh call to `POST /v4/login` using the exact project
key.

In Episort, the Settings action labelled `Retest` uses the connection tester
and performs a fresh TVDB login. It does not use the metadata response cache.

Test procedure:

1. Close Episort completely.
2. Start the intended current build with the intended project key.
3. Open Settings.
4. Run `Retest`.
5. Record the result and HTTP status without recording the key, PIN, bearer
   token, or response credentials.

Interpretation:

- HTTP 200 with a newly issued token: TVDB still accepts the key technically,
  despite the administrative `Inactive` label.
- HTTP 401: the submitted credentials are not accepted.
- HTTP 403: access is forbidden or disabled.
- HTTP 429: temporary rate limiting, not evidence of contractual activation.

A normal metadata search is not sufficient evidence because Episort caches
searches and series for 7 days and movies for 30 days. A previously issued
TVDB bearer token may also remain valid for up to one month.

## Recommended next actions

1. Contact TVDB support and request approval of Episort under the
   negotiated-contract model and the current sub-$50,000 revenue tier.
2. Explain that Episort is a desktop application making direct TVDB v4 calls
   and ask TVDB to confirm that this distribution model is authorised without
   individual subscriber PINs.
3. Add the required clickable TVDB attribution. Episort currently displays
   attribution text in its About screen, but it is a plain JavaFX `Label`
   rather than a direct link.
4. After approval, create or rotate to a new key. Never reactivate or reuse the
   historically exposed key.
5. Remove the credential from Git history with a coordinated history rewrite,
   then update every affected remote branch, tag, release artefact, and clone.
6. Keep release credentials in CI secrets and inject them only during the
   approved build process. Treat any key present in a desktop binary as
   extractable.
7. Add monitoring that distinguishes authentication failures, authorisation
   failures, and rate limiting.
8. Replace immediate retries with exponential backoff and support for the
   `Retry-After` response header.
9. Review the TVDB revenue tier, attribution, project description, and contact
   details annually.

If TVDB does not authorise direct desktop access with a shared project key,
Episort should use either:

- a user-supported project key with a personal TVDB PIN for each user; or
- an Episort-operated caching proxy that retains the TVDB key server-side.

## Draft support request

> Subject: Episort negotiated-contract API key approval
>
> Hello,
>
> Episort is a desktop application that directly accesses TheTVDB API v4 to
> identify television series and episodes and prepare user-reviewed file
> organisation plans.
>
> The project currently generates less than USD 50,000 in annual revenue. The
> dashboard shows the funding model as "Negotiated Contract" and the status as
> "Inactive".
>
> We will display TheTVDB attribution with a direct link in the application.
> Could you confirm whether Episort qualifies for the free licensing tier,
> whether direct access from distributed desktop installations is authorised
> without individual subscriber PINs, and what is required to approve and
> activate the project?
>
> We will rotate the current development credential before distribution.
>
> Thank you.

Do not include the full API key, a subscriber PIN, or a bearer token in the
support request.

## Sources

- TVDB API pricing, annual tier review, and attribution:
  <https://thetvdb.com/api-information>
- TVDB v4 key application and approval process:
  <https://support.thetvdb.com/kb/faq.php?id=81>
- TVDB access models:
  <https://support.thetvdb.com/kb/faq.php?id=62>
- TVDB v4 authentication:
  <https://thetvdb.github.io/v4-api/>
- TVDB recommendations for direct clients and caching proxies:
  <https://github.com/thetvdb/v4-api>
- FileBot statement that its TVDB key is built into the application:
  <https://www.filebot.net/forums/viewtopic.php?t=11453>
- FileBot statement about its commercial TVDB licence:
  <https://www.filebot.net/forums/viewtopic.php?t=12266>
- FileBot v4 login and delayed retry example:
  <https://www.filebot.net/forums/viewtopic.php?t=14255>
