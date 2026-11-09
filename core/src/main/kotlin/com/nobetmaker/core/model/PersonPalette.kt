package com.nobetmaker.core.model

/** Kişi renkleri: ton olarak birbirinden olabildiğince uzak, takvimde ve çıktılarda dolu renk olarak kullanılır. */
object PersonPalette {
    val colors = listOf(
        "#E03131", // kırmızı
        "#1C6DD0", // mavi
        "#F08C00", // turuncu
        "#2F9E44", // yeşil
        "#FCC419", // sarı
        "#7048E8", // mor
        "#15AABF", // camgöbeği
        "#D6336C", // pembe
        "#8B5E3C", // kahverengi
        "#94D82D", // fıstık yeşili
        "#343A40", // antrasit
    )

    /** 0.2.x'e kadarki renkler → yeni karşılıkları (eski veriyi güncellemek için). */
    val legacy = mapOf(
        "#E91E63" to colors[0], "#2196F3" to colors[1], "#FF9800" to colors[2], "#4CAF50" to colors[3],
        "#FFC107" to colors[4], "#9C27B0" to colors[5], "#00BCD4" to colors[6], "#F44336" to colors[7],
        "#3F51B5" to colors[8], "#8BC34A" to colors[9], "#795548" to colors[8], "#607D8B" to colors[10],
    )
}
