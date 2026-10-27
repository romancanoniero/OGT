// Diagnostic probes against the isolated ogt-qa backend. These expose missing
// story behavior; they are not acceptance tests for an implemented feature.
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const base = 'http://127.0.0.1:19083';
const emulator = 'http://127.0.0.1:19099';
const signup = await fetch(`${emulator}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: `stories-audit-${Date.now()}@example.test`, password: 'OnlyGoodThings-QA-2468', returnSecureToken: true }),
});
assert.equal(signup.status, 200);
const { idToken: token } = await signup.json();
const request = (path, data = {}, authenticated = true) => fetch(`${base}/api/v1/${path}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', ...(authenticated ? { Authorization: `Bearer ${token}` } : {}) },
  body: JSON.stringify(data),
});
const api = async (path, data = {}) => {
  const response = await request(path, data);
  const body = await response.json();
  assert.equal(response.ok && body.success, true, `${path}: ${JSON.stringify(body)}`);
  return body.data;
};
const sql = statement => execFileSync('docker', ['compose', '-f', 'qa/compose.yml', 'exec', '-T', 'postgres', 'psql', '-U', 'ogt', '-d', 'onlygoodthings', '-tAc', statement], { cwd: root, encoding: 'utf8' }).trim();
const profile = await api('auth/session', { token });
const bytes = await readFile(new URL('../../backend/src/test/resources/images/transparent.webp', import.meta.url));
const upload = await api('media/upload', { filename: 'story-audit.webp', bytesBase64: bytes.toString('base64') });
const published = await api('social/publish', {
  body: 'Historia QA: auditoría local aislada', topic: 'Comunidad',
  media: [{ kind: 'IMAGE', url: upload.url }],
  isStory: true, storyExpiresAtEpochMs: Date.now() + 3_600_000,
});
assert.match(published.id, /^[0-9a-f-]{36}$/);
assert.equal(published.isStory, false);
assert.equal(sql(`SELECT is_story::text || '|' || (story_expires_at IS NULL)::text FROM social_posts WHERE id='${published.id}'::uuid`), 'false|true');
const feed = () => api('social/feed', { mode: 'HOME', pageSize: 40, cursor: '0' });
assert.equal((await feed()).some(p => p.id === published.id), true);
sql(`UPDATE social_posts SET is_story=true,story_expires_at=now()+interval '1 hour' WHERE id='${published.id}'::uuid`);
assert.equal((await feed()).some(p => p.id === published.id), false);
const authorPosts = () => api('social/posts-by-author', { userId: profile.id, pageSize: 40 });
assert.equal((await authorPosts()).some(p => p.id === published.id && p.isStory), true);
sql(`UPDATE social_posts SET story_expires_at=now()-interval '1 hour' WHERE id='${published.id}'::uuid`);
assert.equal((await authorPosts()).some(p => p.id === published.id), true);
assert.deepEqual(await api('social/visibility', { postIds: [published.id] }), [published.id]);
const mediaStatus = (await fetch(upload.url)).status;
assert.equal(mediaStatus, 200);
const missingRouteStatus = (await request('social/stories')).status;
assert.equal(missingRouteStatus, 404);
const anonymousPublishStatus = (await request('social/publish', {}, false)).status;
assert.equal(anonymousPublishStatus, 401);
await api('social/post/delete', { postId: published.id });
assert.deepEqual(await api('social/visibility', { postIds: [published.id] }), []);
const result = {
  generatedAtUtc: new Date().toISOString(), environment: 'isolated ogt-qa / Firebase emulator',
  featureAcceptance: 'NOT IMPLEMENTED; diagnostic probes confirm gaps',
  observed: {
    publishIgnoresStoryFlagAndExpiry: true,
    ordinaryFeedExcludesRowsMarkedAsStories: true,
    authorListingReturnsStoriesAfterExpiry: true,
    visibilityValidationIgnoresStoryExpiry: true,
    expiredStoryMediaHttpStatus: mediaStatus,
    attemptedStoriesEndpointHttpStatus: missingRouteStatus,
    anonymousPublishHttpStatus: anonymousPublishStatus,
    genericOwnerRemovalInvalidatesVisibility: true,
  },
  productionWrites: false,
};
await writeFile(new URL('../artifacts/stories-audit.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
