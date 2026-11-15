package app.stocklist.app.domain.catalogue

data class UnitOption(val id: String, val code: String, val name: String, val builtIn: Boolean)

/** The 14 built-in units. Same ids and codes as the server's V2 migration (checked by a unit test). */
object BuiltInUnits {
    private fun id(n: Int) = "00000000-0000-0000-0000-%012x".format(n)

    val all: List<UnitOption> = listOf(
        UnitOption(id(1), "PCS", "Pieces", true),
        UnitOption(id(2), "KG", "Kilogram", true),
        UnitOption(id(3), "GRAM", "Gram", true),
        UnitOption(id(4), "LITRE", "Litre", true),
        UnitOption(id(5), "ML", "Millilitre", true),
        UnitOption(id(6), "PACKET", "Packet", true),
        UnitOption(id(7), "BOX", "Box", true),
        UnitOption(id(8), "DOZEN", "Dozen", true),
        UnitOption(id(9), "BUNDLE", "Bundle", true),
        UnitOption(id(10), "METER", "Meter", true),
        UnitOption(id(11), "CARTON", "Carton", true),
        UnitOption(id(12), "BOTTLE", "Bottle", true),
        UnitOption(id(13), "BAG", "Bag", true),
        UnitOption(id(14), "OTHER", "Other", true),
    )

    val PCS: String = all.first().id
}
