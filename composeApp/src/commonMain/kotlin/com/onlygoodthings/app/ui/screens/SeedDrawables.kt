package com.onlygoodthings.app.ui.screens

import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_photo_arboles
import com.onlygoodthings.app.resources.feed_photo_bebederos
import com.onlygoodthings.app.resources.feed_story_compost
import com.onlygoodthings.app.resources.feed_story_donacion
import com.onlygoodthings.app.resources.feed_story_patitas
import com.onlygoodthings.app.resources.feed_story_playa
import com.onlygoodthings.app.resources.feed_story_solar
import com.onlygoodthings.app.resources.publish_photo
import com.onlygoodthings.app.resources.seed_n01
import com.onlygoodthings.app.resources.seed_n01b
import com.onlygoodthings.app.resources.seed_n02
import com.onlygoodthings.app.resources.seed_n02b
import com.onlygoodthings.app.resources.seed_n03
import com.onlygoodthings.app.resources.seed_n03b
import com.onlygoodthings.app.resources.seed_n04
import com.onlygoodthings.app.resources.seed_n05
import com.onlygoodthings.app.resources.seed_n06
import com.onlygoodthings.app.resources.seed_n07
import com.onlygoodthings.app.resources.seed_n07b
import com.onlygoodthings.app.resources.seed_n08
import com.onlygoodthings.app.resources.seed_n09
import com.onlygoodthings.app.resources.seed_n10
import com.onlygoodthings.app.resources.seed_n10b
import com.onlygoodthings.app.resources.seed_n11
import com.onlygoodthings.app.resources.seed_n11b
import com.onlygoodthings.app.resources.seed_n12
import com.onlygoodthings.app.resources.seed_n13
import com.onlygoodthings.app.resources.seed_n14
import com.onlygoodthings.app.resources.seed_n15
import com.onlygoodthings.app.resources.seed_n15b
import com.onlygoodthings.app.resources.seed_n16
import com.onlygoodthings.app.resources.seed_n17
import com.onlygoodthings.app.resources.seed_n18
import com.onlygoodthings.app.resources.seed_n19
import com.onlygoodthings.app.resources.seed_n20
import com.onlygoodthings.app.resources.seed_n20b
import com.onlygoodthings.app.resources.seed_n21
import com.onlygoodthings.app.resources.seed_n22
import com.onlygoodthings.app.resources.seed_n23
import com.onlygoodthings.app.resources.seed_n23b
import com.onlygoodthings.app.resources.seed_n24
import com.onlygoodthings.app.resources.seed_n25
import com.onlygoodthings.app.resources.seed_n26
import com.onlygoodthings.app.resources.seed_n27
import com.onlygoodthings.app.resources.seed_n28
import com.onlygoodthings.app.resources.seed_n28b
import com.onlygoodthings.app.resources.seed_n29
import com.onlygoodthings.app.resources.seed_n30
import com.onlygoodthings.app.resources.seed_n31
import com.onlygoodthings.app.resources.seed_n32
import com.onlygoodthings.app.resources.seed_n33
import com.onlygoodthings.app.resources.seed_n34
import com.onlygoodthings.app.resources.seed_n35
import com.onlygoodthings.app.resources.seed_n36
import com.onlygoodthings.app.resources.seed_n37
import com.onlygoodthings.app.resources.seed_n38
import com.onlygoodthings.app.resources.seed_n39
import com.onlygoodthings.app.resources.seed_n40
import com.onlygoodthings.app.resources.seed_pet_capybara
import com.onlygoodthings.app.resources.seed_pet_cat
import com.onlygoodthings.app.resources.seed_pet_chinchilla
import com.onlygoodthings.app.resources.seed_pet_dog
import com.onlygoodthings.app.resources.seed_pet_donkey
import com.onlygoodthings.app.resources.seed_pet_duck
import com.onlygoodthings.app.resources.seed_pet_ferret
import com.onlygoodthings.app.resources.seed_pet_goat
import com.onlygoodthings.app.resources.seed_pet_golden
import com.onlygoodthings.app.resources.seed_pet_guinea
import com.onlygoodthings.app.resources.seed_pet_hamster
import com.onlygoodthings.app.resources.seed_pet_hedgehog
import com.onlygoodthings.app.resources.seed_pet_horse
import com.onlygoodthings.app.resources.seed_pet_kitten
import com.onlygoodthings.app.resources.seed_pet_parrot
import com.onlygoodthings.app.resources.seed_pet_pig
import com.onlygoodthings.app.resources.seed_pet_puppy
import com.onlygoodthings.app.resources.seed_pet_rabbit
import com.onlygoodthings.app.resources.seed_pet_tabby
import com.onlygoodthings.app.resources.seed_pet_turtle
import org.jetbrains.compose.resources.DrawableResource

/** Mapea el assetKey del seed a un drawable de composeResources. */
internal fun seedDrawable(key: String): DrawableResource = when (key) {
    "seed_n01" -> Res.drawable.seed_n01
    "seed_n01b" -> Res.drawable.seed_n01b
    "seed_n02" -> Res.drawable.seed_n02
    "seed_n02b" -> Res.drawable.seed_n02b
    "seed_n03" -> Res.drawable.seed_n03
    "seed_n03b" -> Res.drawable.seed_n03b
    "seed_n04" -> Res.drawable.seed_n04
    "seed_n05" -> Res.drawable.seed_n05
    "seed_n06" -> Res.drawable.seed_n06
    "seed_n07" -> Res.drawable.seed_n07
    "seed_n07b" -> Res.drawable.seed_n07b
    "seed_n08" -> Res.drawable.seed_n08
    "seed_n09" -> Res.drawable.seed_n09
    "seed_n10" -> Res.drawable.seed_n10
    "seed_n10b" -> Res.drawable.seed_n10b
    "seed_n11" -> Res.drawable.seed_n11
    "seed_n11b" -> Res.drawable.seed_n11b
    "seed_n12" -> Res.drawable.seed_n12
    "seed_n13" -> Res.drawable.seed_n13
    "seed_n14" -> Res.drawable.seed_n14
    "seed_n15" -> Res.drawable.seed_n15
    "seed_n15b" -> Res.drawable.seed_n15b
    "seed_n16" -> Res.drawable.seed_n16
    "seed_n17" -> Res.drawable.seed_n17
    "seed_n18" -> Res.drawable.seed_n18
    "seed_n19" -> Res.drawable.seed_n19
    "seed_n20" -> Res.drawable.seed_n20
    "seed_n20b" -> Res.drawable.seed_n20b
    "seed_n21" -> Res.drawable.seed_n21
    "seed_n22" -> Res.drawable.seed_n22
    "seed_n23" -> Res.drawable.seed_n23
    "seed_n23b" -> Res.drawable.seed_n23b
    "seed_n24" -> Res.drawable.seed_n24
    "seed_n25" -> Res.drawable.seed_n25
    "seed_n26" -> Res.drawable.seed_n26
    "seed_n27" -> Res.drawable.seed_n27
    "seed_n28" -> Res.drawable.seed_n28
    "seed_n28b" -> Res.drawable.seed_n28b
    "seed_n29" -> Res.drawable.seed_n29
    "seed_n30" -> Res.drawable.seed_n30
    "seed_n31" -> Res.drawable.seed_n31
    "seed_n32" -> Res.drawable.seed_n32
    "seed_n33" -> Res.drawable.seed_n33
    "seed_n34" -> Res.drawable.seed_n34
    "seed_n35" -> Res.drawable.seed_n35
    "seed_n36" -> Res.drawable.seed_n36
    "seed_n37" -> Res.drawable.seed_n37
    "seed_n38" -> Res.drawable.seed_n38
    "seed_n39" -> Res.drawable.seed_n39
    "seed_n40" -> Res.drawable.seed_n40
    "seed_pet_dog" -> Res.drawable.seed_pet_dog
    "seed_pet_puppy" -> Res.drawable.seed_pet_puppy
    "seed_pet_cat" -> Res.drawable.seed_pet_cat
    "seed_pet_kitten" -> Res.drawable.seed_pet_kitten
    "seed_pet_rabbit" -> Res.drawable.seed_pet_rabbit
    "seed_pet_hamster" -> Res.drawable.seed_pet_hamster
    "seed_pet_guinea" -> Res.drawable.seed_pet_guinea
    "seed_pet_parrot" -> Res.drawable.seed_pet_parrot
    "seed_pet_turtle" -> Res.drawable.seed_pet_turtle
    "seed_pet_horse" -> Res.drawable.seed_pet_horse
    "seed_pet_donkey" -> Res.drawable.seed_pet_donkey
    "seed_pet_goat" -> Res.drawable.seed_pet_goat
    "seed_pet_duck" -> Res.drawable.seed_pet_duck
    "seed_pet_hedgehog" -> Res.drawable.seed_pet_hedgehog
    "seed_pet_ferret" -> Res.drawable.seed_pet_ferret
    "seed_pet_chinchilla" -> Res.drawable.seed_pet_chinchilla
    "seed_pet_pig" -> Res.drawable.seed_pet_pig
    "seed_pet_capybara" -> Res.drawable.seed_pet_capybara
    "seed_pet_golden" -> Res.drawable.seed_pet_golden
    "seed_pet_tabby" -> Res.drawable.seed_pet_tabby
    "feed_story_playa" -> Res.drawable.feed_story_playa
    "feed_story_patitas" -> Res.drawable.feed_story_patitas
    "feed_story_compost" -> Res.drawable.feed_story_compost
    "feed_photo_arboles" -> Res.drawable.feed_photo_arboles
    "feed_photo_bebederos" -> Res.drawable.feed_photo_bebederos
    "feed_story_donacion" -> Res.drawable.feed_story_donacion
    "feed_story_solar" -> Res.drawable.feed_story_solar
    "jardin", "publish_photo" -> Res.drawable.publish_photo
    "arboles" -> Res.drawable.feed_photo_arboles
    else -> Res.drawable.feed_story_donacion
}
