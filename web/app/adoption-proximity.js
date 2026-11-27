(function(scope){
const point = row => row?.latitude != null && row?.longitude != null && Number.isFinite(Number(row.latitude)) && Number.isFinite(Number(row.longitude)) && Math.abs(Number(row.latitude)) <= 90 && Math.abs(Number(row.longitude)) <= 180;
function adoptionNearby(post, animal, {mode='HOME', radiusKm=20, viewer=null, ownerId=''}={}) {
  if (mode !== 'HOME' || animal?.kind !== 'ADOPTION' || radiusKm === 0 || post.authorId === ownerId) return true;
  if (!point(viewer) || !point(animal)) return false;
  const radians = value => Number(value) * Math.PI / 180;
  const dLat = radians(animal.latitude - viewer.latitude), dLng = radians(animal.longitude - viewer.longitude);
  const h = Math.sin(dLat/2)**2 + Math.cos(radians(viewer.latitude))*Math.cos(radians(animal.latitude))*Math.sin(dLng/2)**2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h))) <= radiusKm * 1000;
}

scope.OgtAdoptionNearby=adoptionNearby;
})(globalThis);
