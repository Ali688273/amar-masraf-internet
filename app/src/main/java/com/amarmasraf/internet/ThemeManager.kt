package com.amarmasraf.internet

import android.content.Context
import android.graphics.Color

object ThemeManager {
    data class Palette(
        val background: String, val card: String, val dark: String, val primary: String,
        val accent: String, val text: String, val muted: String, val soft: String,
        val download: String, val upload: String, val danger: String
    )

    private val palettes = linkedMapOf(
        "midnight" to Palette("#0F172A", "#202E49", "#0B1220", "#6366F1", "#818CF8", "#FFFFFF", "#A8B6CC", "#D8E1F0", "#34D399", "#60A5FA", "#F87171"),
        "ocean" to Palette("#071A2B", "#12445A", "#04111D", "#0891B2", "#22D3EE", "#F0FDFA", "#A5D8EA", "#CFFAFE", "#4ADE80", "#38BDF8", "#FB7185"),
        "forest" to Palette("#0B1F17", "#1B4935", "#07140F", "#15803D", "#34D399", "#F0FDF4", "#B3C9BC", "#D1FAE5", "#4ADE80", "#60A5FA", "#F87171"),
        "sunset" to Palette("#241313", "#522B2B", "#170B0B", "#C2410C", "#FB923C", "#FFF7ED", "#F5C7A5", "#FED7AA", "#34D399", "#60A5FA", "#FDA4AF"),
        "light" to Palette("#EEF2FF", "#FFFFFF", "#DDE5F5", "#4F46E5", "#7C3AED", "#111827", "#64748B", "#334155", "#059669", "#2563EB", "#DC2626"),
        "rose" to Palette("#2A1220", "#52233D", "#1B0B15", "#DB2777", "#F472B6", "#FFF1F7", "#E8B6CE", "#FBCFE8", "#34D399", "#60A5FA", "#FB7185"),
        "lavender" to Palette("#1C1733", "#352A5C", "#120F24", "#7C3AED", "#A78BFA", "#F5F3FF", "#C4B5FD", "#EDE9FE", "#34D399", "#60A5FA", "#FB7185"),
        "emerald" to Palette("#062B28", "#10534B", "#031B19", "#0D9488", "#2DD4BF", "#ECFDF5", "#A7D8CF", "#CCFBF1", "#4ADE80", "#60A5FA", "#FB7185")
    )

    private val names = linkedMapOf(
        "midnight" to "نیمه‌شب", "ocean" to "اقیانوس", "forest" to "جنگل",
        "sunset" to "غروب", "light" to "روشن", "rose" to "رز صورتی",
        "lavender" to "بنفش یاسی", "emerald" to "زمردی"
    )

    fun current(context: Context): String =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", "midnight") ?: "midnight"

    fun name(id: String): String = names[id] ?: names.getValue("midnight")
    fun ids(): List<String> = palettes.keys.toList()

    fun color(context: Context, role: String): Int {
        val p = palettes[current(context)] ?: palettes.getValue("midnight")
        val hex = when (role) {
            "background" -> p.background
            "card" -> p.card
            "cardAlt" -> if (current(context) == "light") "#E0E7FF" else p.primary
            "dark" -> p.dark
            "primary" -> p.primary
            "accent" -> p.accent
            "text" -> p.text
            "muted" -> p.muted
            "soft" -> p.soft
            "download" -> p.download
            "upload" -> p.upload
            "danger" -> p.danger
            else -> p.card
        }
        return Color.parseColor(hex)
    }
}
