package com.example.util

object Localization {
    private val fa = mapOf(
        "SETTINGS & CONFIGURATION" to "تنظیمات و پیکربندی",
        "THEME SELECTION" to "انتخاب تم پوسته",
        "THEME" to "پوسته",
        "AUDIO SYSTEMS CONSOLE" to "تنظیمات سیستم‌های صوتی",
        "Play Move / Menu Click Sounds" to "پخش صدای حرکت و کلیک منو",
        "Synthesizer Master Volume" to "حجم صدای سنتسایزر",
        "ANTI-CHEAT GATEWAY STATUS" to "وضعیت سپر ضدتقلب",
        "SHIELD V3 ACTIVE" to "سپر ضدتقلب نسخه ۳ فعال است",
        "Server-side Move coordinate validation active" to "اعتبارسنجی مختصات حرکت سمت سرور فعال است",
        "Turn alternate verification matching client sockets" to "تأیید نوبت متناوب مطابق سوکت کلاینت فعال است",
        "Dynamic timing delta mapping guarding Elo protection values" to "حفاظت زمانی پویا از مقادیر تراز رتبه‌بندی (الّو) فعال است",
        "Tamper logs synced to admin reports on Firebase consoles" to "گزارش‌های همگام‌سازی تداخلات به پنل ادمین فایربیس",
        "INFINITE" to "بی‌نهایت",
        "TIC TAC TOE" to "دوز بی‌نهایت آنلاین",
        "SELECT BATTLEGROUND" to "انتخاب میدان نبرد دوزبازی",
        "Ranked Duel" to "دوئل رتبه‌بندی (رقابتی ELO)",
        "Compete for Elo tier & Climb Leaderboards" to "رقابت برای ارتقای رتبه الّو و جدول امتیازات",
        "Casual Arena" to "میدان بازی دوستانه (کژوال)",
        "Friendly quick multiplayer clash" to "نبرد چندنفره سریع و غیررقابتی با بقیه دوزبازان",
        "Offline vs AI" to "بازی با هوش مصنوعی (آفلاین)",
        "Train with 6 custom adaptive difficulty bots" to "تمرین با ۶ ربات هوش مصنوعی سازگار با سختی‌های متفاوت",
        "Pass & Play" to "دونفره آفلاین (پاس و بازی)",
        "Two player battle on one single device" to "نبرد دونفره شانه به شانه روی همین موبایل",
        "Daily Gift" to "هدیه روزانه",
        "Leaderboards" to "برترین دوزبازان",
        "TRAINING ENTRANCE" to "ورود به بخش تمرینی",
        "Select AI Difficulty Intellect:" to "انتخاب درجه سختی هوش مصنوعی:",
        "Match Series Count (Rematches):" to "تعداد دورهای مسابقه (بهترین از):",
        "Single Match" to "تک بازی",
        "Best of" to "بهترین از",
        "START DUEL" to "شروع دوئل بازی",
        "Cancel" to "انصراف",
        "SERVER: OPERATIONAL" to "وضعیت سرور: فعال و آنلاین",
        "Online Players:" to "کاربران آنلاین:",
        "Online Players: " to "کاربران آنلاین: ",
        "Players Online" to "بازیکنان آنلاین",
        "Active Rooms" to "اتاق‌های فعال",
        "Your Wins" to "بردهای شما",
        "Opp Wins" to "بردهای حریف",
        "Playing as" to "بازی به عنوان",
        "👉 YOUR ACTION TURN" to "👉 نوبت حرکت شماست",
        "⏱️ OPPONENT MEDITATING..." to "⏱️ حریف در حال فکر کردن...",
        "Center board" to "وسط‌چین کردن صفحه",
        "Zoom In" to "بزرگنمایی",
        "Zoom Out" to "کوچکنمایی",
        "Abandon Duel" to "تسلیم",
        "Confirm Abandon" to "تأیید خروج و تسلیم",
        "Are you sure you want to surrender and leave the game? This will count as a defeat and your ELO rating will be reduced." to "آیا از خروج و تسلیم در بازی اطمینان دارید؟ خروج از بازی فعال شکست محسوب شده و باعث کاهش ریتینگ بازیکنی شما خواهد شد.",
        "Defeat & Exit" to "تسلیم و خروج",
        "Stay in Battle" to "ادامه بازی",
        "Confirm Exit" to "تأیید خروج",
        "Are you sure you want to return to the lobby? Your session achievements are saved." to "آیا مطمئن هستید که می‌خواهید به لابی اصلی بازی بازگردید؟",
        "Exit" to "خروج",
        "MATCH COMPLETE!" to "مسابقه پایان یافت!",
        "VICTORY!" to "پیروزی شیرین!",
        "DEFEAT" to "شکست!",
        "Congratulations local champion!" to "تبریک به قهرمان محلی!",
        "Series secured! You crushed the duel." to "به‌به! پیروزی در سری مسابقات تضمین شد.",
        "Series lost. Learn and redeploy your tactics!" to "سری مسابقات را باختید. از تاکتیک‌های جدید استفاده کنید!",
        "Intermission. Play dynamic setups!" to "وقفه میانی. چیدمان‌های پویا را بازی کنید!",
        "📈 ELO INCREASE" to "📈 افزایش امتیاز الّو",
        "📉 ELO DEMOTION" to "📉 کاهش امتیاز الّو",
        "ELO Record point" to "امتیاز الّو ثبت شد",
        "ranking loss" to "کاهش رتبه‌بندی",
        "REQUEST REMATCH" to "درخواست بازی مجدد",
        "ACCEPT REMATCH" to "قبول درخواست بازی مجدد",
        "Opponent requested a rematch!" to "حریف درخواست بازی مجدد داده است!",
        "REQUEST SENT..." to "درخواست ارسال شد...",
        "Waiting for opponent response..." to "در انتظار پاسخ حریف...",
        "RETURN TO PORT" to "بازگشت به لابی",
        "LANGUAGE LANGUAGE" to "انتخاب زبان برنامه",
        "Language Selection" to "انتخاب زبان",
        "ENGLISH" to "English (انگلیسی)",
        "PERSIAN" to "فارسی (Persian)",
        "SETTINGS" to "تنظیمات",
        "SIGN OUT" to "خروج از حساب",
        "Elo Rating" to "رتبه‌بندی الّو",
        "Win Rate" to "نرخ پیروزی",
        "Level" to "سطح",
        "XP" to "تجربه (XP)"
    )

    fun translate(text: String, lang: String): String {
        if (lang == "EN") return text
        // Exact matching
        val trimmed = text.trim()
        val mapped = fa[trimmed]
        if (mapped != null) return mapped

        // Fallback checks for keywords
        for ((k, v) in fa) {
            if (trimmed.equals(k, ignoreCase = true)) {
                return v
            }
            if (trimmed.contains(k, ignoreCase = true)) {
                return trimmed.replace(k, v, ignoreCase = true)
            }
        }
        return text
    }

    fun getCountryFlagAndName(countryCode: String, lang: String): Pair<String, String> {
        val code = countryCode.uppercase().trim()
        if (code.length != 2) {
            return Pair("🌏", if (lang == "FA") "نامشخص" else "Unknown")
        }
        
        val flag = try {
            val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        } catch (e: Exception) {
            "🌏"
        }
        
        val name = when (code) {
            "IR" -> if (lang == "FA") "ایران" else "Iran"
            "US" -> if (lang == "FA") "آمریکا" else "United States"
            "GB" -> if (lang == "FA") "انگلستان" else "United Kingdom"
            "CA" -> if (lang == "FA") "کانادا" else "Canada"
            "DE" -> if (lang == "FA") "آلمان" else "Germany"
            "FR" -> if (lang == "FA") "فرانسه" else "France"
            "JP" -> if (lang == "FA") "ژاپن" else "Japan"
            "CN" -> if (lang == "FA") "چین" else "China"
            "RU" -> if (lang == "FA") "روسیه" else "Russia"
            "TR" -> if (lang == "FA") "ترکیه" else "Turkey"
            "AE" -> if (lang == "FA") "امارات" else "United Arab Emirates"
            "IN" -> if (lang == "FA") "هند" else "India"
            "ES" -> if (lang == "FA") "اسپانیا" else "Spain"
            "IT" -> if (lang == "FA") "ایتالیا" else "Italy"
            "BR" -> if (lang == "FA") "برزیل" else "Brazil"
            "AU" -> if (lang == "FA") "استرالیا" else "Australia"
            else -> {
                try {
                    val locale = java.util.Locale("", code)
                    val countryName = locale.getDisplayCountry(if (lang == "FA") java.util.Locale("fa") else java.util.Locale.ENGLISH)
                    if (countryName.isNotBlank()) countryName else code
                } catch (e: Exception) {
                    code
                }
            }
        }
        return Pair(flag, name)
    }
}
