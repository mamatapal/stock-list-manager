package app.stocklist.supplier.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stocklist.supplier.core.designsystem.FormField
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.util.Validators
import app.stocklist.supplier.data.remote.OrgDto
import app.stocklist.supplier.data.remote.ProfileRequest

/** The business details, as typed. Used by registration and by "Correct details". */
class BusinessFormState {
    var legalName by mutableStateOf("")
    var tradeName by mutableStateOf("")
    var contactPerson by mutableStateOf("")
    var mobile by mutableStateOf("")
    var address by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var pinCode by mutableStateOf("")
    var gstin by mutableStateOf("")

    /** Field errors after the last check; empty when everything is fine. */
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun fill(org: OrgDto) {
        legalName = org.legalName
        tradeName = org.tradeName.orEmpty()
        contactPerson = org.contactPerson
        mobile = org.mobile
        address = org.address
        city = org.city
        state = org.state
        pinCode = org.pinCode
        gstin = org.gstin.orEmpty()
    }

    fun check(): Boolean {
        val e = linkedMapOf<String, String>()
        Validators.required(legalName, 150)?.let { e["legalName"] = it }
        Validators.optional(tradeName, 150)?.let { e["tradeName"] = it }
        Validators.required(contactPerson, 120)?.let { e["contactPerson"] = it }
        Validators.mobile(mobile)?.let { e["mobile"] = it }
        Validators.required(address, 300)?.let { e["address"] = it }
        Validators.required(city, 80)?.let { e["city"] = it }
        Validators.required(state, 80)?.let { e["state"] = it }
        Validators.pinCode(pinCode)?.let { e["pinCode"] = it }
        Validators.gstin(gstin)?.let { e["gstin"] = it }
        errors = e
        return e.isEmpty()
    }

    fun addErrors(more: Map<String, String>) {
        errors = errors + more
    }

    fun toProfile() = ProfileRequest(
        legalName = legalName.trim(),
        tradeName = tradeName.trim().ifEmpty { null },
        contactPerson = contactPerson.trim(),
        mobile = Validators.cleanMobile(mobile),
        address = address.trim(),
        city = city.trim(),
        state = state.trim(),
        pinCode = pinCode.trim(),
        gstin = gstin.trim().uppercase().ifEmpty { null },
    )
}

@Composable
fun BusinessFields(form: BusinessFormState, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(form.legalName, { form.legalName = it }, tr("Business name (as registered)"),
            error = form.errors["legalName"], capitalization = KeyboardCapitalization.Words, enabled = enabled)
        FormField(form.tradeName, { form.tradeName = it }, tr("Shop or trade name (optional)"),
            error = form.errors["tradeName"], capitalization = KeyboardCapitalization.Words, enabled = enabled)
        FormField(form.contactPerson, { form.contactPerson = it }, tr("Contact person"),
            error = form.errors["contactPerson"], capitalization = KeyboardCapitalization.Words, enabled = enabled)
        FormField(form.mobile, { form.mobile = it }, tr("Mobile number"), error = form.errors["mobile"],
            keyboardType = KeyboardType.Phone, enabled = enabled)
        FormField(form.address, { form.address = it }, tr("Address"), error = form.errors["address"],
            singleLine = false, enabled = enabled)
        FormField(form.city, { form.city = it }, tr("City"), error = form.errors["city"],
            capitalization = KeyboardCapitalization.Words, enabled = enabled)
        FormField(form.state, { form.state = it }, tr("State"), error = form.errors["state"],
            capitalization = KeyboardCapitalization.Words, enabled = enabled)
        FormField(form.pinCode, { form.pinCode = it }, tr("PIN code"), error = form.errors["pinCode"],
            keyboardType = KeyboardType.Number, enabled = enabled)
        FormField(form.gstin, { form.gstin = it.uppercase() }, tr("GSTIN (optional)"), error = form.errors["gstin"],
            capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, enabled = enabled)
    }
}

/** Which form field a server error code belongs to, so it can be shown next to that field. */
fun fieldOf(code: String): String? = when (code) {
    "NAME_REQUIRED" -> "legalName"
    "CONTACT_REQUIRED" -> "contactPerson"
    "ADDRESS_REQUIRED" -> "address"
    "INVALID_MOBILE" -> "mobile"
    "INVALID_PIN_CODE" -> "pinCode"
    "INVALID_GSTIN" -> "gstin"
    "INVALID_EMAIL" -> "email"
    "WEAK_PASSWORD" -> "password"
    else -> null
}
