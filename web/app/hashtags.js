const folds = {á:'a',à:'a',ä:'a',â:'a',é:'e',è:'e',ë:'e',ê:'e',í:'i',ì:'i',ï:'i',î:'i',ó:'o',ò:'o',ö:'o',ô:'o',ú:'u',ù:'u',ü:'u',û:'u',ñ:'n'};
export function hashtagKey(raw) {
  const text=String(raw).trim().replace(/^#/,'');
  if(!text || text.length>60 || !/^[\p{L}\p{N}_\s]+$/u.test(text))throw new Error('Usá letras, números y guion bajo en los hashtags.');
  const key=[...text.toLowerCase()].map(c=>folds[c]||c).join('').replace(/\s/g,'');
  if(key.length<2 || key.length>40 || !/\p{L}/u.test(key))throw new Error('El hashtag debe tener entre 2 y 40 caracteres e incluir letras.');
  return key;
}
export function hashtagList(raw,{preserveLabels=false}={}) {
  const tags=String(raw).split(/[\s,]+/).filter(Boolean);
  const unique=[...new Map(tags.map(tag=>[hashtagKey(tag),tag.replace(/^#/,'')])).entries()];
  if(!unique.length || unique.length>8)throw new Error('Elegí entre 1 y 8 hashtags.');
  return unique.map(([key,label])=>preserveLabels?label:key);
}
