package app.stocklist.app.domain.catalogue

import app.stocklist.app.core.i18n.trKey

data class UnitOption(val id: String, val code: String, val name: String, val builtIn: Boolean) {
    /** Built-in unit names are shown in the app's language; units the business added keep their own name. */
    val displayName: String get() = if (builtIn) app.stocklist.app.core.i18n.trMessage(name) else name
}

/** The 14 built-in units. Same ids and codes as the server's V2 migration (checked by a unit test). */
object BuiltInUnits {
    private fun id(n: Int) = "00000000-0000-0000-0000-%012x".format(n)

    val all: List<UnitOption> = listOf(
        UnitOption(id(1), "PCS", trKey("Pieces"), true),
        UnitOption(id(2), "KG", trKey("Kilogram"), true),
        UnitOption(id(3), "GRAM", trKey("Gram"), true),
        UnitOption(id(4), "LITRE", trKey("Litre"), true),
        UnitOption(id(5), "ML", trKey("Millilitre"), true),
        UnitOption(id(6), "PACKET", trKey("Packet"), true),
        UnitOption(id(7), "BOX", trKey("Box"), true),
        UnitOption(id(8), "DOZEN", trKey("Dozen"), true),
        UnitOption(id(9), "BUNDLE", trKey("Bundle"), true),
        UnitOption(id(10), "METER", trKey("Meter"), true),
        UnitOption(id(11), "CARTON", trKey("Carton"), true),
        UnitOption(id(12), "BOTTLE", trKey("Bottle"), true),
        UnitOption(id(13), "BAG", trKey("Bag"), true),
        UnitOption(id(14), "OTHER", trKey("Other"), true),
    )

    val PCS: String = all.first().id
}
