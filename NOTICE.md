# Notices

H AGENT is an independent Android application.

The project integrates with GitHub APIs and external AI/image providers. Availability, limits, model names, queue behavior, and free tiers are controlled by those providers and may change independently of this repository.

AI Horde support is intended for free image generation, while an optional image API endpoint can be supplied at build time. The coding runtime may use provider-routing infrastructure configured by the project/runtime.

No GitHub access token, image API key, or user secret should be committed to this repository. User credentials persisted by the Android app are stored through Android Keystore-backed encryption.

Legacy internal identifiers may remain solely to preserve Android upgrade/signing compatibility and previously encrypted local data.
