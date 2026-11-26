import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated local services.');
test('convocatoria is an upcoming organized activity with its own form and persisted date and place',async({page,context,request})=>{
 await context.route('**/*',r=>['localhost','127.0.0.1'].includes(new URL(r.request().url()).hostname)?r.continue():r.abort());
 await page.goto('/#/registro');await page.getByLabel('Nombre y apellido',{exact:true}).fill('Organizadora QA');await page.getByLabel('Correo electrónico',{exact:true}).fill(`qa-gathering-${randomUUID()}@example.test`);await page.getByLabel('Contraseña',{exact:true}).fill('OnlyGoodThings-QA-2468');await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();await expect(page).toHaveURL(/#\/inicio/);
 const token=await page.evaluate(()=>window.OgtAuth.getToken());
 await page.goto('/#/publicar/convocatoria');await expect(page.getByRole('heading',{name:'Convocatoria',exact:true})).toBeVisible();await expect(page.locator('[name=honoree]')).toHaveCount(0);await expect(page.getByText('¿Quién hizo esta buena acción?',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Reforestación',exact:true}).click();await page.getByLabel('Día y hora de la actividad',{exact:true}).fill('2030-11-26T10:30');await page.getByLabel('Lugar o punto de encuentro',{exact:true}).fill('Entrada del parque comunitario');await page.getByLabel('La historia',{exact:true}).fill('Nos juntamos a plantar árboles. Traigan guantes.');
 await page.locator('[data-file]').setInputFiles({name:'gathering.jpg',mimeType:'image/jpeg',buffer:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))});
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/social/publish'));await page.locator('[data-submit]').click();const result=await response;expect(result.ok(),await result.text()).toBe(true);const saved=(await result.json()).data;
 expect(saved.publicationKind).toBe('CONVOCATORIA');expect(saved.protagonistUserId??null).toBeNull();expect(saved.honoreeName??null).toBeNull();expect(saved.place).toBe('Entrada del parque comunitario');expect(saved.eventStartsAtEpochMs).toBe(await page.evaluate(()=>new Date('2030-11-26T10:30').getTime()));
 await page.reload();await expect(page.locator('#main')).toContainText('Entrada del parque comunitario');await expect(page.locator('#main')).toContainText('Nos juntamos a plantar árboles');
 const edit=await request.post('/api/v1/social/post/edit',{headers:{Authorization:'Bearer '+token},data:{postId:saved.id,body:'La convocatoria continúa con el horario acordado.'}});expect(edit.ok()).toBe(true);const edited=(await edit.json()).data;expect(edited.eventStartsAtEpochMs).toBe(saved.eventStartsAtEpochMs);expect(edited.place).toBe(saved.place);expect(edited.media).toEqual(saved.media);
});
