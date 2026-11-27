import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const context = vm.createContext({});
vm.runInContext(readFileSync(new URL('../web/app/post-carousel.js', import.meta.url), 'utf8'), context);
const carousel = context.OgtPostCarousel;

test('mixed carousels preserve every file and its order, poster and frame with one dot per item', () => {
  const post = {media: [
    {url: 'https://example.test/last.jpg', kind: 'IMAGE', sortOrder: 2},
    {url: 'https://example.test/first.jpg', kind: 'IMAGE', sortOrder: 0, frameX: .2},
    {url: 'https://example.test/middle.mp4', kind: 'VIDEO', sortOrder: 1, posterUrl: 'https://example.test/poster.jpg'},
  ]};
  const html = carousel.html(post, {frameOf: p => ({x: p.media[0].frameX ?? .5, y: .5, zoom: 1})});
  assert.deepEqual(Array.from(carousel.rows(post), item => item.url), ['https://example.test/first.jpg', 'https://example.test/middle.mp4', 'https://example.test/last.jpg']);
  assert.equal((html.match(/data-media-go=/g) || []).length, 3);
  assert.equal((html.match(/class="post-media-slide"/g) || []).length, 3);
  assert.equal((html.match(/aria-current="true"/g) || []).length, 1);
  assert.ok(html.includes('<video class="media" src="https://example.test/middle.mp4" poster="https://example.test/poster.jpg" playsinline'));
  assert.ok(!html.includes('<img class="media" src="https://example.test/middle.mp4"'));
  assert.ok(!html.includes(' controls'));
  assert.ok(html.includes('data-video-play'));
  assert.ok(html.includes('data-video-sound'));
  assert.ok(!html.includes(' loop'));
  assert.ok(html.includes('data-frame-x="0.2"'));
  assert.ok(html.includes('Ir a video 2 de 3'));
  assert.equal(post.media[0].url, 'https://example.test/last.jpg');
});

test('single images retain the established slot without dots, legacy videos play and empty posts have no gallery', () => {
  const single = carousel.html({mediaUrls: ['photo.jpg']});
  assert.ok(single.includes('<div class="media-slot"><img class="media" src="photo.jpg"'));
  assert.ok(!single.includes('data-media-go='));
  assert.ok(carousel.html({mediaUrls: ['video.mp4?version=2']}).includes('<video class="media"'));
  assert.equal(carousel.html({media: [], mediaUrls: []}), '');
});

test('media metadata is escaped before becoming markup', () => {
  const html = carousel.html({media: [{kind: 'VIDEO', url: 'video.mp4" onerror="bad', posterUrl: 'poster.jpg" onerror="bad', altText: '<script>bad</script>'}]});
  assert.ok(!html.includes('src="video.mp4" onerror='));
  assert.ok(!html.includes('poster="poster.jpg" onerror='));
  assert.ok(html.includes('&quot; onerror=&quot;'));
});
