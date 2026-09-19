package de.tiktokshop.buchhaltung.ui.common

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.formatGerman

fun Cents.asEuro(): String = "${formatGerman()} €"
