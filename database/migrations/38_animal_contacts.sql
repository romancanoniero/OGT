-- Additive: existing conversations, animal listings and messages remain intact.
CREATE TABLE IF NOT EXISTS animal_contacts (
    match_id UUID PRIMARY KEY REFERENCES timebank_matches(id) ON DELETE CASCADE,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    listing_id UUID REFERENCES animal_listings(id) ON DELETE SET NULL,
    applicant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('ADOPTION','SIGHTING')),
    listing_title TEXT NOT NULL,
    request_payload JSONB NOT NULL,
    initial_message_id UUID REFERENCES timebank_messages(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(listing_id,applicant_user_id,kind)
);
CREATE INDEX IF NOT EXISTS animal_contacts_listing ON animal_contacts(listing_id,created_at DESC);
