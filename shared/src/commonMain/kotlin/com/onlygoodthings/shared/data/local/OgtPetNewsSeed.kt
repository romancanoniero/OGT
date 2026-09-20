package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostPersonRole

/** 100 historias tiernas de mascotas. sourceUrl solo si hay nota real; si no, foto Commons del tipo. */
internal object OgtPetNewsSeed {
    data class PetStory(
        val id: String,
        val author: String,
        val place: String,
        val hoursAgo: Double,
        val tag: String,
        val body: String,
        val sourceUrl: String?,
        val photo: String,
        val impact: Int,
    )

    val items: List<PetStory> = listOf(
        PetStory("pet-001", OgtIds.Mariana, "Usaquén, Bogotá", 4.0, "Adopción", "Medusa, de ocho años y rescatada de maltrato, salió de la Unidad de Cuidado Animal en San Valentín. Nataly y Juan David se enamoraron de su historia y se la llevaron a casa.", "https://www.elespectador.com/la-red-zoocial/perros/eligen-a-medusa-una-de-las-perritas-mas-viejas-en-jornada-de-adopcion-en-bogota/", "seed_pet_dog", 47),
        PetStory("pet-002", OgtIds.Ana, "Bariloche, Argentina", 9.0, "Adopción", "Mauro pasó más de un año en Sanidad Animal con leptospirosis y brucelosis. Una voluntaria de Dejando Huellas lo paseaba cada semana y hoy lo adoptó.", "https://infobariloche.com.ar/sociedad/bariloche/mauro-el-perro-que-supero-dos-enfermedades-y-finalmente-encontro-una-familia-en-bariloche.html", "seed_pet_dog", 54),
        PetStory("pet-003", OgtIds.Bruno, "Usaquén, Bogotá", 16.0, "Adopción", "Pirata Patria, criollo de ocho años, tardó casi dos años en hallar familia después de un rescate grave. Hoy vive con Frank y una nena de cuatro.", "https://www.pulzo.com/vivir-bien/mascotas/adopcion-en-bogota-la-emotiva-recuperacion-de-pirata-patria-con-el-idpyba-PP5193405A", "seed_pet_dog", 61),
        PetStory("pet-004", OgtIds.Carla, "Winter Park, Florida", 11.0, "Rescate animal", "Pilotos voluntarios de Puppy Rescue Flights ya trasladaron más de 12.000 perros y gatos desde refugios saturados hacia hogares en Florida.", "https://www.wftv.com/news/local/winter-park-nonprofit-transports-12000-animals-florida-rescues/QBDFXKCZDRBBDPOQ2GV27PIU4E/", "seed_pet_puppy", 68),
        PetStory("pet-005", OgtIds.Lucas, "Iztapalapa, CDMX", 28.0, "Adopción", "Cereza y Cerecito, sobrevivientes de la explosión de 2025, cumplieron un año juntos en Huellitas Amor Sin Fronteras.", "https://www.milenio.com/comunidad/cereza-cerecito-perritos-sobrevivientes-iztapalapa-regresan-a-hogar", "seed_pet_puppy", 75),
        PetStory("pet-006", OgtIds.Sofia, "Ciudad de México", 22.0, "Adopción", "CDMX habilitó amaresadoptar.cdmx.gob.mx para dar hogar a perros del Franciscano, Ajusco y la Brigada. El trámite pide visita y contrato de por vida.", "https://www.dossierdeprensa.mx/impulsa-brugada-adopcion-responsable-de-animales-rescatados-inicia-proceso-con-plataforma-digital/", "seed_pet_dog", 82),
        PetStory("pet-007", OgtIds.CarlosG, "Cuajimalpa, CDMX", 33.0, "Adopción", "156 perros volvieron al Franciscano y el Gobierno abrió el canal formal de adopción responsable.", "https://www.cronica.com.mx/metropoli/2026/09/14/regresan-156-perros-al-refugio-franciscano-cdmx-inicia-programa-de-adopcion-responsable/", "seed_pet_dog", 89),
        PetStory("pet-008", OgtIds.Camila, "Unicentro, Bogotá", 19.0, "Adopción", "El IDPYBA sigue abriendo jornadas en shoppings y pide casa también para adultos y perros en tratamiento.", "https://www.semana.com/4patas/perros/articulo/en-video-la-historia-de-pirata-patria-el-perro-que-fue-rescatado-en-un-estado-de-salud-grave-y-duro-casi-dos-anos-sin-hogar/202625/", "seed_pet_golden", 96),
        PetStory("pet-009", OgtIds.Mateo, "Palermo Soho, CABA", 2.0, "Tránsito animal", "Luna, gata atigrada de tres meses, pasó la noche en un living de Honduras y Charcas. Mañana busca casa definitiva; come solita y ronronea si le hablás bajo.", null, "seed_pet_kitten", 103),
        PetStory("pet-010", OgtIds.Nicolas, "Villa Crespo, CABA", 5.0, "Adopción", "Coco llevaba dos semanas en el techo de un taller. Lo bajamos con atún y una sábana. Ya está castrado y duerme en una caja de zapatos.", null, "seed_pet_cat", 110),
        PetStory("pet-011", OgtIds.Valeria, "Colegiales, CABA", 7.0, "Rescate animal", "Un cachorro mestizo se quedó dormido en la puerta del almacén. Le pusimos Pipo. El vet del barrio le sacó las pulgas y ya mueve la cola si escuchás llaves.", null, "seed_pet_puppy", 117),
        PetStory("pet-012", OgtIds.CarlosR, "Caballito, CABA", 8.0, "Adopción", "Mora, de siete años, esperaba en el canil del club. Una vecina la pasea a las 7 y a las 19. Buscamos alguien que no le tema a las canas.", null, "seed_pet_dog", 124),
        PetStory("pet-013", OgtIds.Lucia, "Almagro, CABA", 10.0, "Tránsito animal", "Nube, coneja blanca, llegó en una caja de verduras. Come heno y se esconde detrás de la heladera. Tres noches de tránsito y ya busca familia calma.", null, "seed_pet_rabbit", 131),
        PetStory("pet-014", OgtIds.MateoG, "Parque Chacabuco, CABA", 12.0, "Mascotas", "Dos cuises, Tita y Tito, se quedaron sin casa cuando se mudó el tercer piso. Los tenemos en el balcón tapado. Quien los adopte, se lleva la jaula limpia.", null, "seed_pet_guinea", 138),
        PetStory("pet-015", OgtIds.ValeriaP, "Flores, CABA", 13.0, "Rescate animal", "En la plaza de Rivadavia un pastor mestizo se sentó al lado de la hamaca y no se fue. Lo llamamos Simón. Le dimos agua y ahora espera en el hall del edificio.", null, "seed_pet_dog", 145),
        PetStory("pet-016", OgtIds.Joaquin, "Boedo, CABA", 15.0, "Adopción", "Menta, gata atigrada sorda de un oído, se queda si le hablás de frente. El refugio de Estados Unidos la tiene lista: vacunas al día, busca depto sin perros grandes.", null, "seed_pet_tabby", 152),
        PetStory("pet-017", OgtIds.MarianaD, "San Telmo, CABA", 17.0, "Tránsito animal", "Un perico verde se posó en el aljibe de Defensa. Le decimos Loro Pepe. Come manzana y dice «hola» si abrís la ventana. Buscamos criador responsable o vecino con voladera.", null, "seed_pet_parrot", 159),
        PetStory("pet-018", OgtIds.DiegoF, "La Boca, CABA", 18.0, "Rescate animal", "Tres cachorros color caramelo aparecieron detrás de la cancha. Ya comieron y duermen amontonados. El sábado hay jornada de castración; hasta entonces, tránsito en Caminito.", null, "seed_pet_puppy", 166),
        PetStory("pet-019", OgtIds.Roberto, "Belgrano, CABA", 21.0, "Adopción", "Olivia, golden de diez años, se quedó sin familia por un viaje largo. Camina despacio y se acuesta al sol. No es un perro de corrida: es un perro de siesta.", null, "seed_pet_golden", 173),
        PetStory("pet-020", OgtIds.Lucas, "Núñez, CABA", 23.0, "Mascotas", "Una tortuga de orejas rojas llegó a la escuela 12 en una palangana. Los chicos le pusieron Rita. El club de ciencias arma un terrario y busca un adulto que la adopte en serio.", null, "seed_pet_turtle", 180),
        PetStory("pet-021", OgtIds.Mariana, "Saavedra, CABA", 24.0, "Tránsito animal", "Pancho, hámster sirio, se escapó del aula y apareció en el guardapolvo de la maestra. Come pipas y duerme de día. Buscamos casa sin gatos curiosos.", null, "seed_pet_hamster", 187),
        PetStory("pet-022", OgtIds.Ana, "Chacarita, CABA", 26.0, "Adopción", "Grisú, gato negro de un solo ojo, vive en la veterinaria de Corrientes. Se sube a las computadoras. Ideal para alguien que trabaje en casa y le guste el silencio.", null, "seed_pet_cat", 194),
        PetStory("pet-023", OgtIds.Bruno, "Paternal, CABA", 27.0, "Rescate animal", "Nala se lastimó una pata en las vías. La operaron en el hospital de campana. Hoy apoya las cuatro y busca un patio chico, no un percherón.", null, "seed_pet_dog", 41),
        PetStory("pet-024", OgtIds.Carla, "Villa Urquiza, CABA", 29.0, "Adopción", "Cuatro gatitos naranja nacieron detrás del chino. La mamá ya está castrada. Se van de a uno: el primero se llama Sol y cabe en una taza.", null, "seed_pet_kitten", 48),
        PetStory("pet-025", OgtIds.Lucas, "Coghlan, CABA", 31.0, "Mascotas", "Nieve, chinchilla, se quedó sin dueño en un depto de venta. Baño de arena los martes. No es un juguete: es un compañero nocturno que pide calma.", null, "seed_pet_chinchilla", 55),
        PetStory("pet-026", OgtIds.Sofia, "Villa Devoto, CABA", 32.0, "Tránsito animal", "Kiko, hurón, se metió en un caño de desagüe y salió cubierto de barro. Ya está seco y duerme en una remera. Buscamos alguien que conozca la especie.", null, "seed_pet_ferret", 62),
        PetStory("pet-027", OgtIds.CarlosG, "Versalles, CABA", 34.0, "Adopción", "Pancha, burra de 12 años, llegó de un predio de Liniers. Come pasto y se deja cepillar. El club de polo le presta un corral hasta que aparezca un campo chico.", null, "seed_pet_donkey", 69),
        PetStory("pet-028", OgtIds.Camila, "Mataderos, CABA", 35.0, "Rescate animal", "Lucero, caballo flaco, lo bajaron de un carro. Come avena y ya no tiembla. Un vecino del Mercado lo pasea al paso. Buscamos padrinazgo para el fardo.", null, "seed_pet_horse", 76),
        PetStory("pet-029", OgtIds.Mateo, "Liniers, CABA", 37.0, "Adopción", "Pepa, cabra enana, se comió los geranios de la esquina y se quedó. Es mansa y da compañía. Ideal quinta o terraza reforzada, no balcón de 2 m.", null, "seed_pet_goat", 83),
        PetStory("pet-030", OgtIds.Nicolas, "Constitución, CABA", 38.0, "Tránsito animal", "Un pato doméstico cruzó la 9 de Julio. Le decimos Julio. Nada en una palangana y sigue a quien le tira lechuga. El zoológico barrial lo tiene 48 h.", null, "seed_pet_duck", 90),
        PetStory("pet-031", OgtIds.Valeria, "Barracas, CABA", 39.0, "Rescate animal", "Erizo encontrado en un tacho de compost. Le pusimos Púa. Come tenebrios y se enrolla si hay ruido. No es silvestre de acá: alguien lo soltó y ahora busca casa.", null, "seed_pet_hedgehog", 97),
        PetStory("pet-032", OgtIds.CarlosR, "Parque Patricios, CABA", 41.0, "Adopción", "Lola, mini pig de 18 kg, no entra más en el depto. Es limpia y gruñe si le cantás. Buscamos casa con patio de tierra, no pileta de plástico.", null, "seed_pet_pig", 104),
        PetStory("pet-033", OgtIds.Lucia, "Pompeya, CABA", 43.0, "Mascotas", "Un carpincho joven apareció en el Riachuelo y se sentó en la vereda. Prefectura lo llevó a un centro. Mientras tanto, los pibes le dejaron sandía en la reja.", null, "seed_pet_capybara", 111),
        PetStory("pet-034", OgtIds.MateoG, "Villa Lugano, CABA", 44.0, "Adopción", "Tano, mestizo de plaza, espera en el CIC. Se lleva bien con chicos y le tiene miedo a los globos. Ideal familia paciente, no departamento de fiestas.", null, "seed_pet_dog", 118),
        PetStory("pet-035", OgtIds.ValeriaP, "Villa Soldati, CABA", 46.0, "Tránsito animal", "Cinco cachorros bajo un auto estacionado. La conductora avisó y nos quedamos hasta que arrancó. Ya comieron. Falta desparasitar y encontrar cinco hogares.", null, "seed_pet_puppy", 125),
        PetStory("pet-036", OgtIds.Joaquin, "Villa Riachuelo, CABA", 47.0, "Adopción", "Doña Rosa, gata de 11 años, se quedó en el merendero cuando cerró el local. Come pollo desmenuzado y se sienta en las sillas. Buscamos casa sin mudanzas.", null, "seed_pet_cat", 132),
        PetStory("pet-037", OgtIds.MarianaD, "Villa Pueyrredón, CABA", 49.0, "Rescate animal", "Beto se trabó en una reja. Los bomberos lo sacaron sin un rasguño. Hoy duerme en un living de Mosconi. Dueño no apareció: se publica en el grupo del barrio.", null, "seed_pet_dog", 139),
        PetStory("pet-038", OgtIds.DiegoF, "Agronomía, CABA", 51.0, "Mascotas", "La facultad recibió un conejo angora que alguien ató a un árbol. Le cortamos los nudos y le cepillamos el pelo. Se llama Algodón y busca tutor que sepa de lana.", null, "seed_pet_rabbit", 146),
        PetStory("pet-039", OgtIds.Roberto, "Villa Ortúzar, CABA", 52.0, "Adopción", "Trío de gatitos grises en el cantero de la plaza. La mamá come y se deja tocar. Adoptamos en tándem: no separamos a la camada si se puede evitar.", null, "seed_pet_kitten", 153),
        PetStory("pet-040", OgtIds.Lucas, "Palermo Hollywood, CABA", 53.0, "Tránsito animal", "Un siamés cruzado se coló en un ensayo de teatro. Se quedó dormido en el sofá del vestuario. Se llama Telón. 72 h de espera por el dueño y después adopción.", null, "seed_pet_cat", 160),
        PetStory("pet-041", OgtIds.Mariana, "Palermo Viejo, CABA", 54.0, "Adopción", "Rita, galga mestiza, corre poco y se acuesta a los pies. Salió de un predio de Apóstoles. Ideal depto alto: no salta a la calle, mira por la ventana.", null, "seed_pet_dog", 167),
        PetStory("pet-042", OgtIds.Ana, "Las Cañitas, CABA", 56.0, "Mascotas", "Cata, cotorra, imita el timbre del edificio. La familia se mudó al exterior y la dejó con la portera. Buscamos voladera o criadero habilitado, no jaula chica.", null, "seed_pet_parrot", 174),
        PetStory("pet-043", OgtIds.Bruno, "Recoleta, CABA", 57.0, "Adopción", "Monsieur, gato blanco sordo, vive en la veterinaria de Quintana. Se comunica con la cola. Ideal casa sin balcón abierto: no escucha los autos.", null, "seed_pet_cat", 181),
        PetStory("pet-044", OgtIds.Carla, "Retiro, CABA", 58.0, "Rescate animal", "Un ovejero se sentó en el andén de Retiro tres tardes seguidas. El personal de estación le puso agua. Hoy está en tránsito en un PH de San Martín.", null, "seed_pet_dog", 188),
        PetStory("pet-045", OgtIds.Lucas, "Puerto Madero, CABA", 59.0, "Tránsito animal", "Tortuga hallada en un cantero de los diques. No es marina: es de agua dulce. El acuario barrial la tiene hasta que alguien arme un tanque de verdad.", null, "seed_pet_turtle", 195),
        PetStory("pet-046", OgtIds.Sofia, "Monserrat, CABA", 61.0, "Adopción", "Beto Jr., mestizo de dos meses, cabe en una mochila pero no viaja en subte. Vacunas el jueves. Buscamos primer perro para alguien que esté en casa a la siesta.", null, "seed_pet_puppy", 42),
        PetStory("pet-047", OgtIds.CarlosG, "San Nicolás, CABA", 62.0, "Mascotas", "Tres hámsteres de un kiosco que cerró. Comen mix y corren de noche. Se adoptan juntos o de a uno, pero no como souvenir de oficina.", null, "seed_pet_hamster", 49),
        PetStory("pet-048", OgtIds.Camila, "Balvanera, CABA", 63.0, "Rescate animal", "Misha se quedó trabada en un entretecho. La bajamos con atún y linterna. Tiene un collar sin chapita. 48 h en el grupo del Once y después se publica adopción.", null, "seed_pet_cat", 56),
        PetStory("pet-049", OgtIds.Mateo, "Once, CABA", 64.0, "Adopción", "Chicho, caniche mestizo sénior, espera en la peluquería canina. Le cortaron nudos y le quedó cara de león. Busca sofá, no plaza de domingo lleno.", null, "seed_pet_dog", 63),
        PetStory("pet-050", OgtIds.Nicolas, "Abasto, CABA", 66.0, "Tránsito animal", "Cuis tricolor encontrado en un pasillo del shopping. Le pusimos Abasto. Come morrón y silba si hay gente. Ideal chico con adultos responsables.", null, "seed_pet_guinea", 70),
        PetStory("pet-051", OgtIds.Valeria, "Villa del Parque, CABA", 67.0, "Adopción", "Tomás, golden de 9 años, se quedó ciego de un ojo. Camina el pasillo sin chocar. Una familia lo tuvo 8 años y se mudó al exterior: busca continuidad, no lástima.", null, "seed_pet_golden", 77),
        PetStory("pet-052", OgtIds.CarlosR, "Villa Santa Rita, CABA", 68.0, "Mascotas", "Pata doméstica en la plazoleta de Nazca. Los vecinos le armaron un tacho con rampa. No es fauna nativa: alguien la soltó. Buscamos quinta con pileta baja.", null, "seed_pet_duck", 84),
        PetStory("pet-053", OgtIds.Lucia, "Floresta, CABA", 69.0, "Rescate animal", "Lola se escapó de un traslado y apareció en la estación. El guardabarrera le dio galletas. Ya está identificada: el dueño vive a seis cuadras y lloró en el andén.", null, "seed_pet_dog", 91),
        PetStory("pet-054", OgtIds.MateoG, "Vélez Sársfield, CABA", 71.0, "Adopción", "Nube y Carbón, hermanos de 8 semanas, no se sueltan. El refugio pide adopción doble. Comen papilla y se pelean por un pompón.", null, "seed_pet_kitten", 98),
        PetStory("pet-055", OgtIds.ValeriaP, "Monte Castro, CABA", 72.0, "Tránsito animal", "Gato naranja con un bigote quemado. El vet dice que está bien. Se llama Bigote. Duerme en la caja de la impresora. Buscamos depto sin perros celosos.", null, "seed_pet_cat", 105),
        PetStory("pet-056", OgtIds.Joaquin, "Villa Real, CABA", 73.0, "Adopción", "Rocco, boxer mestizo, babea y se ríe. El canil municipal lo tiene listo. Ideal casa con reja baja: salta poco, ladra si pasa el carrito de la verdura.", null, "seed_pet_dog", 112),
        PetStory("pet-057", OgtIds.MarianaD, "Versalles Oeste, CABA", 74.0, "Mascotas", "Una yegua pampa pastaba en un baldío. Rural la revisó: no tiene dueño visible. Come fardo donado. Buscamos campo de tránsito, no selfie de plaza.", null, "seed_pet_horse", 119),
        PetStory("pet-058", OgtIds.DiegoF, "Villa Luro, CABA", 76.0, "Adopción", "Fela, gata atigrada de 6 años, espera en un PH. Se lleva con perros chicos. El dueño anterior se internó: la familia pide un hogar que no la devuelva en un mes.", null, "seed_pet_tabby", 126),
        PetStory("pet-059", OgtIds.Roberto, "Liniers Norte, CABA", 77.0, "Rescate animal", "Cachorra atada a un poste con hilo. La cortamos y la llevamos al vet. Se llama Hilo. Come y duerme. Mañana desparasitación; después, adopción con contrato.", null, "seed_pet_puppy", 133),
        PetStory("pet-060", OgtIds.Lucas, "Mataderos Sur, CABA", 78.0, "Tránsito animal", "Chivo joven en un baldío de Alberdi. Come yuyo y se deja tocar. No es de faena: es de compañía. El predio vecinal lo tiene 72 h.", null, "seed_pet_goat", 140),
        PetStory("pet-061", OgtIds.Mariana, "Villa Soldati Este, CABA", 79.0, "Adopción", "Cira, mestiza sorda, se comunica con la mano. El entrenador del barrio le enseñó «sentado» con señas. Ideal casa sin calle abierta.", null, "seed_pet_dog", 147),
        PetStory("pet-062", OgtIds.Ana, "Parque Avellaneda, CABA", 81.0, "Mascotas", "Conejo negro suelto en el parque. Lo atrapamos con lechuga. Se llama Sombra. Dientes largos: hay que llevarlo al vet exótico. Buscamos tutor que sepa eso.", null, "seed_pet_rabbit", 154),
        PetStory("pet-063", OgtIds.Bruno, "Villa General Mitre, CABA", 82.0, "Adopción", "Pepa, gata calico, vive en la peluquería. Se sube a las sillas y no se va. La dueña se jubila. Buscamos casa con ventanas con red.", null, "seed_pet_cat", 161),
        PetStory("pet-064", OgtIds.Carla, "Paternal Norte, CABA", 83.0, "Rescate animal", "Un cruza galgo se desmayó de calor en la 24. Lo mojamos y lo llevaron al hospital. Hoy bebe solo. Se llama Sombra también: hay que ponerle chapita.", null, "seed_pet_dog", 168),
        PetStory("pet-065", OgtIds.Lucas, "Chacarita Sur, CABA", 84.0, "Tránsito animal", "Loro hablador en un árbol de Guzmán. Dice el nombre de una nena. Publicamos el audio en el grupo. Si no aparece dueño, va a un criadero habilitado.", null, "seed_pet_parrot", 175),
        PetStory("pet-066", OgtIds.Sofia, "Colegiales Este, CABA", 86.0, "Adopción", "Mora Jr., mestiza de 10 semanas, ya sabe dónde está el diario. El refugio pide familia con patio o terrazas cerradas. No es un perro de 8 h sola.", null, "seed_pet_puppy", 182),
        PetStory("pet-067", OgtIds.CarlosG, "Belgrano R, CABA", 87.0, "Mascotas", "Erizo africano dejado en una caja en el veterinario. Come pienso especial. No es de suelta en plaza: se muere de frío. Buscamos tutor informado.", null, "seed_pet_hedgehog", 189),
        PetStory("pet-068", OgtIds.Camila, "Núñez Río, CABA", 88.0, "Adopción", "Greta, golden de 4 años, se quedó sin familia por alergia de un nene. Es mansa y se moja las patas en el bebedero. El club de remo la pasea hasta que haya casa.", null, "seed_pet_golden", 196),
        PetStory("pet-069", OgtIds.Mateo, "Saavedra Parque, CABA", 89.0, "Tránsito animal", "Gatito blanco en el motor de un auto. El dueño del auto avisó antes de arrancar. Se llama Radiador. Cabe en una mano. Leche de reemplazo cada 4 h.", null, "seed_pet_kitten", 43),
        PetStory("pet-070", OgtIds.Nicolas, "Coghlan Este, CABA", 91.0, "Rescate animal", "Toby se tragó un hilo. Operación de 40 minutos y ya come pollo. El dueño no tiene para la cuenta: el barrio juntó el resto. Hoy duerme en casa, no en jaula.", null, "seed_pet_dog", 50),
        PetStory("pet-071", OgtIds.Valeria, "Villa Urquiza Norte, CABA", 92.0, "Adopción", "Sombra, gata negra de 3 años, espera en el consultorio. Le dicen que da mala suerte: nosotros decimos que da siesta. Buscamos alguien sin supersticiones.", null, "seed_pet_cat", 57),
        PetStory("pet-072", OgtIds.CarlosR, "Devoto Norte, CABA", 93.0, "Mascotas", "Cuis pelado de laboratorio que un alumno trajo a casa. Es rosa y come heno. No es un juguete de feria. El club de ciencias busca familia que entienda eso.", null, "seed_pet_guinea", 64),
        PetStory("pet-073", OgtIds.Lucia, "Villa Pueyrredón Este, CABA", 94.0, "Tránsito animal", "Hurón escapado de un balcón. Lo atrapamos con un trapo. Se llama Cinta. Vacunas al día según el chip. Dueño avisado: se reencuentran esta tarde.", null, "seed_pet_ferret", 71),
        PetStory("pet-074", OgtIds.MateoG, "Villa Ortúzar Sur, CABA", 96.0, "Adopción", "Pancho, mestizo de 5 años, espera en el canil de la sociedad de fomento. Se lleva con gatos. Ideal PH con patio interno.", null, "seed_pet_dog", 78),
        PetStory("pet-075", OgtIds.ValeriaP, "Palermo Botanico, CABA", 97.0, "Rescate animal", "Pato herido en el lago. Guardaparques lo sacó y lo llevaron a un centro. Come arvejas. No es mascota de bañadera: cuando sane, vuelve al agua grande.", null, "seed_pet_duck", 85),
        PetStory("pet-076", OgtIds.Joaquin, "Palermo Soho Sur, CABA", 98.0, "Adopción", "Lino, gato atigrado, se quedó en un Airbnb. Los huéspedes se fueron. La portera lo alimenta. Buscamos adopción con contrato, no «mientras tanto».", null, "seed_pet_tabby", 92),
        PetStory("pet-077", OgtIds.MarianaD, "Villa Crespo Norte, CABA", 99.0, "Mascotas", "Chinchilla en una jaula chica dejada en la vereda. Ya tiene baño de arena y una jaula más alta. Se llama Polvo. Buscamos tutor que no la bañe con agua.", null, "seed_pet_chinchilla", 99),
        PetStory("pet-078", OgtIds.DiegoF, "Almagro Sur, CABA", 101.0, "Tránsito animal", "Cachorro con un ojito lloroso. El vet dice que es conjuntivitis, no ceguera. Se llama Guiño. Tres días de gotas y después adopción.", null, "seed_pet_puppy", 106),
        PetStory("pet-079", OgtIds.Roberto, "Caballito Norte, CABA", 102.0, "Adopción", "Emma, labradora mestiza, espera en el club. Nada en la pileta de lona. Ideal familia que camine de mañana, no que la deje 10 h.", null, "seed_pet_dog", 113),
        PetStory("pet-080", OgtIds.Lucas, "Flores Sur, CABA", 103.0, "Rescate animal", "Gato atascado en un árbol de Rivadavia. Bomberos lo bajaron. No tiene chip. 24 h en el grupo y después se publica como adopción.", null, "seed_pet_cat", 120),
        PetStory("pet-081", OgtIds.Mariana, "Floresta Norte, CABA", 104.0, "Adopción", "Miel, gatita canela, cabe en un tazón. Come y ronronea. El refugio pide que no se separe de su hermano Tostada si se puede.", null, "seed_pet_kitten", 127),
        PetStory("pet-082", OgtIds.Ana, "Monte Castro Este, CABA", 106.0, "Mascotas", "Tortuga terrestre en un cantero. No es de suelta: es de terrario seco. El club de reptiles la tiene. Buscamos adulto, no regalo de cumpleaños.", null, "seed_pet_turtle", 134),
        PetStory("pet-083", OgtIds.Bruno, "Versalles Sur, CABA", 107.0, "Adopción", "Burro joven en un predio de la General Paz. Come y se deja tocar las orejas. Una ONG rural busca campo a menos de 80 km.", null, "seed_pet_donkey", 141),
        PetStory("pet-084", OgtIds.Carla, "Mataderos Norte, CABA", 108.0, "Tránsito animal", "Chancho vietnamita en un baldío. No es de faena familiar: es mascota que gruñe. El predio vecinal lo tiene hasta el domingo.", null, "seed_pet_pig", 148),
        PetStory("pet-085", OgtIds.Lucas, "Liniers Sur, CABA", 109.0, "Adopción", "Cabra con un cencerro. Se llama Campana. Come yuyo y se sube a las sillas. Ideal quinta, no terraza de 4 m.", null, "seed_pet_goat", 155),
        PetStory("pet-086", OgtIds.Sofia, "Villa Luro Este, CABA", 111.0, "Rescate animal", "Perro atropellado leve en Rivadavia. El conductor se bajó y pagó la radiografía. No tiene dueño. Hoy cojea poco. Se llama Rivadavia, mal nombre, buen final.", null, "seed_pet_dog", 162),
        PetStory("pet-087", OgtIds.CarlosG, "Villa Real Norte, CABA", 112.0, "Adopción", "Tita, gata sénior, espera en la veterinaria. Come blando. Ideal persona mayor que quiera compañía quieta.", null, "seed_pet_cat", 169),
        PetStory("pet-088", OgtIds.Camila, "Villa Devoto Sur, CABA", 113.0, "Mascotas", "Poni dejado en un baldío. Come y ya no tiene llagas. El club hípico le presta box. Buscamos padrino para el fardo, no jinete de fin de semana.", null, "seed_pet_horse", 176),
        PetStory("pet-089", OgtIds.Mateo, "Villa Urquiza Sur, CABA", 114.0, "Tránsito animal", "Conejo enano en una plaza. Lo atrapamos con perejil. Se llama Perejil. Dientes: control en 10 días. Buscamos tutor de exóticos.", null, "seed_pet_rabbit", 183),
        PetStory("pet-090", OgtIds.Nicolas, "Saavedra Este, CABA", 116.0, "Adopción", "Luna Jr., mestiza blanca, ya sabe sentarse. El refugio pide familia con otro perro manso o con tiempo. No es un perro de 9 a 18 fuera.", null, "seed_pet_puppy", 190),
        PetStory("pet-091", OgtIds.Valeria, "Núñez Norte, CABA", 117.0, "Rescate animal", "Un cruza collie se perdió en la costanera. El dueño publicó foto y a las dos horas lo tenían en el parrilla. Se abrazaron. Chapita nueva esta semana.", null, "seed_pet_dog", 197),
        PetStory("pet-092", OgtIds.CarlosR, "Belgrano C, CABA", 118.0, "Adopción", "Max, golden de 6 años, se quedó sin patio. El dueño pide adopción responsable con jardín. Es un perro de agua: si hay pileta, mejor.", null, "seed_pet_golden", 44),
        PetStory("pet-093", OgtIds.Lucia, "Colegiales Oeste, CABA", 119.0, "Mascotas", "Agapornis en pareja, dejados en una jaula en el chino. Comen alpiste y se acicalan. Se adoptan juntos. No son decoración de living.", null, "seed_pet_parrot", 51),
        PetStory("pet-094", OgtIds.MateoG, "Chacarita Norte, CABA", 121.0, "Tránsito animal", "Gatito en un contenedor. Lo sacamos con guantes. Come y ya no tiembla. Se llama Contenedor, vamos a cambiarle el nombre cuando haya casa.", null, "seed_pet_kitten", 58),
        PetStory("pet-095", OgtIds.ValeriaP, "Villa Crespo Sur, CABA", 122.0, "Adopción", "Rita, gata atigrada de 2 años, espera en un PH. Se lleva con perros. El dueño se va a estudiar. Buscamos continuidad, no «prueba de 15 días».", null, "seed_pet_tabby", 65),
        PetStory("pet-096", OgtIds.Joaquin, "Palermo Norte, CABA", 123.0, "Rescate animal", "Gato en un conducto de aire. El técnico lo sacó y se quedó en la oficina. Se llama Split. 48 h de aviso y después adopción interna del edificio.", null, "seed_pet_cat", 72),
        PetStory("pet-097", OgtIds.MarianaD, "Recoleta Sur, CABA", 124.0, "Adopción", "Caniche toy sénior, se llama Monsieur también. Come blando y se sube a la falda. Ideal persona que no viaje cada mes.", null, "seed_pet_dog", 79),
        PetStory("pet-098", OgtIds.DiegoF, "San Telmo Norte, CABA", 126.0, "Mascotas", "Carpincho de peluche no: uno de verdad se asomó en el Riachuelo otra vez. No se toca. Prefectura lo observó y se fue nadando. Historia tierna, no mascota.", null, "seed_pet_capybara", 86),
        PetStory("pet-099", OgtIds.Roberto, "La Boca Norte, CABA", 127.0, "Adopción", "Caminito, mestizo color óxido, espera en el club. Se lleva con gatos. Ideal casa con reja, no puerto de turistas.", null, "seed_pet_puppy", 93),
        PetStory("pet-100", OgtIds.Lucas, "Barracas Sur, CABA", 128.0, "Tránsito animal", "Perra preñada en un baldío. La llevamos al vet: tres cachorros en camino. Tránsito largo. Buscamos casa para la mamá primero, los pibes después.", null, "seed_pet_dog", 100),
    )

    fun append(db: OgtLocalDatabase, now: Long) {
        items.forEachIndexed { index, item ->
            val post = LocalSocialPost(
                id = item.id,
                authorKind = AuthorKind.USER,
                authorUserId = item.author,
                authorCompanyId = null,
                place = item.place,
                timeLabel = timeLabel(item.hoursAgo),
                tag = item.tag,
                body = item.body,
                impactCount = item.impact,
                commentCount = 1 + index % 4,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = now - (item.hoursAgo * 3_600_000).toLong(),
                sourceUrl = item.sourceUrl,
            )
            db.posts += post
            db.postPeople += LocalPostPerson(item.id, item.author, PostPersonRole.AUTHOR)
            db.postPeople += LocalPostPerson(item.id, item.author, PostPersonRole.PROTAGONIST)
            db.postMedia += LocalPostMedia(
                id = "${item.id}-img",
                postId = item.id,
                kind = MediaKind.IMAGE,
                url = "asset://${item.photo}",
                sortOrder = 0,
                assetKey = item.photo,
                altText = item.tag,
            )
        }
    }

    private fun timeLabel(hoursAgo: Double): String = when {
        hoursAgo < 1 -> "Hace ${(hoursAgo * 60).toInt()} min"
        hoursAgo < 24 -> "Hace ${hoursAgo.toInt()} h"
        hoursAgo < 48 -> "Ayer"
        else -> "Hace ${(hoursAgo / 24).toInt()} d"
    }
}
