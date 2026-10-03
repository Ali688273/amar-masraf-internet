package com.amarmasraf.internet

import android.content.Context
import android.graphics.Color

object ThemeManager {
    data class Palette(
        val background: String,
        val card: String,
        val dark: String,
        val primary: String,
        val accent: String,
        val text: String,
        val muted: String,
        val soft: String,
        val download: String,
        val upload: String,
        val danger: String
    )

    private val palettes = linkedMapOf(
        "midnight" to Palette("#0F172A", "#1E293B", "#0B1220", "#6366F1", "#3B82F6", "#FFFFFF", "#94A3B8", "#CBD5E1", "#10B981", "#3B82F6", "#EF4444"),
        "ocean" to Palette("#071A2B", "#0E2A43", "#04111D", "#0891B2", "#06B6D4", "#F0FDFA", "#93C5FD", "#CFFAFE", "#22C55E", "#38BDF8", "#FB7185"),
        "forest" to Palette("#0B1F17", "#15382A", "#07140F", "#16A34A", "#22C55E", "#F0FDF4", "#9CA3AF", "#D1FAE5", "#4ADE80", "#60A5FA", "#F87171"),
        "sunset" to Palette("#241313", "#3A1F1F", "#170B0B", "#EA580C", "#F97316", "#FFF7ED", "#FDBA74", "#FED7AA", "#34D399", "#60A5FA", "#FB7185"),
        "light" to Palette("#F1F5F9", "#FFFFFF", "#E2E8F0", "#4F46E5", "#2563EB", "#0F172A", "#64748B", "#334155", "#059669", "#2563EB", "#DC2626")
    )

    private val names = linkedMapOf(
        "midnight" to "نیمه‌شب",
        "ocean" to "اقیانوس",
        "forest" to "جنگل",
        "sunset" to "غروب",
        "light" to "روشن"
    )

    fun current(context: Context): String =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("theme", "midnight") ?: "midnight"

    fun name(id: String): String = names[id] ?: names.getValue("midnight")

    fun ids(): List<String> = palettes.keys.toList()

    fun color(context: Context, role: String): Int {
        val p = palettes[current(context)] ?: palettes.getValue("midnight")
        val hex = when (role) {
            "background" -> p.background
            "card" -> p.card
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
