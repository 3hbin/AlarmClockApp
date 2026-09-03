package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityWorldClockBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CityTime(
    val cityName: String,
    val country: String,
    val timeZoneId: String
) {
    fun searchBlob(): String =
        "$cityName $country $timeZoneId".lowercase(Locale.getDefault())
}

class WorldClockActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    companion object {
        const val PAYLOAD_TIME = "time"
    }

    private lateinit var binding: ActivityWorldClockBinding
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: WorldClockAdapter
    private val allCities = mutableListOf<CityTime>()

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            try {
                val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
                if (lm != null) {
                    val first = lm.findFirstVisibleItemPosition()
                    val last = lm.findLastVisibleItemPosition()
                    if (first >= 0 && last >= first) {
                        adapter.notifyItemRangeChanged(first, last - first + 1, PAYLOAD_TIME)
                    }
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { BottomNavHelper.bind(this, binding.curvedNav, 1) } catch (_: Exception) {}

        binding.toolbar.setNavigationOnClickListener { Motion.finishFade(this) }

        binding.shimmer.show()
        binding.loadingAnim.applyBrandDefault()
        binding.loadingAnim.visibility = android.view.View.VISIBLE
        binding.loadingAnim.start()
        adapter = WorldClockAdapter(mutableListOf())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.setItemViewCacheSize(12)
        (binding.recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false

        binding.swipeRefresh.setColorSchemeColors(0xFF3F51B5.toInt(), 0xFF7E57C2.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, PAYLOAD_TIME)
            binding.swipeRefresh.isRefreshing = false
        }

        Thread {
            val cities = buildWorldCities().sortedBy { it.cityName.lowercase(Locale.getDefault()) }
            runOnUiThread {
                allCities.clear()
                allCities.addAll(cities)
                adapter.update(allCities)
                binding.tvCount.text = "${allCities.size} múi giờ / thành phố"
                binding.shimmer.hide()
                binding.loadingAnim.stop()
                binding.loadingAnim.visibility = android.view.View.GONE
                binding.recyclerView.scheduleLayoutAnimation()
            }
        }.start()

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        handler.post(updateRunnable)
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) {
            allCities
        } else {
            allCities.filter { it.searchBlob().contains(q) }
        }
        adapter.update(filtered)
        binding.tvCount.text = if (q.isEmpty()) {
            "${allCities.size} múi giờ / thành phố"
        } else {
            "${filtered.size} kết quả cho \"$query\""
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * Danh sách thủ đô + thành phố lớn (~200+ quốc gia/vùng)
     * + toàn bộ TimeZone hệ thống (bổ sung, không trùng).
     */
    private fun buildWorldCities(): List<CityTime> {
        val curated = listOf(
            // Asia
            CityTime("Hà Nội", "Việt Nam", "Asia/Ho_Chi_Minh"),
            CityTime("TP. Hồ Chí Minh", "Việt Nam", "Asia/Ho_Chi_Minh"),
            CityTime("Đà Nẵng", "Việt Nam", "Asia/Ho_Chi_Minh"),
            CityTime("Tokyo", "Nhật Bản", "Asia/Tokyo"),
            CityTime("Osaka", "Nhật Bản", "Asia/Tokyo"),
            CityTime("Seoul", "Hàn Quốc", "Asia/Seoul"),
            CityTime("Busan", "Hàn Quốc", "Asia/Seoul"),
            CityTime("Bắc Kinh", "Trung Quốc", "Asia/Shanghai"),
            CityTime("Thượng Hải", "Trung Quốc", "Asia/Shanghai"),
            CityTime("Hồng Kông", "Trung Quốc", "Asia/Hong_Kong"),
            CityTime("Đài Bắc", "Đài Loan", "Asia/Taipei"),
            CityTime("Singapore", "Singapore", "Asia/Singapore"),
            CityTime("Kuala Lumpur", "Malaysia", "Asia/Kuala_Lumpur"),
            CityTime("Bangkok", "Thái Lan", "Asia/Bangkok"),
            CityTime("Jakarta", "Indonesia", "Asia/Jakarta"),
            CityTime("Denpasar (Bali)", "Indonesia", "Asia/Makassar"),
            CityTime("Manila", "Philippines", "Asia/Manila"),
            CityTime("Phnom Penh", "Campuchia", "Asia/Phnom_Penh"),
            CityTime("Vientiane", "Lào", "Asia/Vientiane"),
            CityTime("Yangon", "Myanmar", "Asia/Yangon"),
            CityTime("Dhaka", "Bangladesh", "Asia/Dhaka"),
            CityTime("Kathmandu", "Nepal", "Asia/Kathmandu"),
            CityTime("Colombo", "Sri Lanka", "Asia/Colombo"),
            CityTime("New Delhi", "Ấn Độ", "Asia/Kolkata"),
            CityTime("Mumbai", "Ấn Độ", "Asia/Kolkata"),
            CityTime("Islamabad", "Pakistan", "Asia/Karachi"),
            CityTime("Karachi", "Pakistan", "Asia/Karachi"),
            CityTime("Kabul", "Afghanistan", "Asia/Kabul"),
            CityTime("Tehran", "Iran", "Asia/Tehran"),
            CityTime("Baghdad", "Iraq", "Asia/Baghdad"),
            CityTime("Riyadh", "Ả Rập Xê Út", "Asia/Riyadh"),
            CityTime("Dubai", "UAE", "Asia/Dubai"),
            CityTime("Abu Dhabi", "UAE", "Asia/Dubai"),
            CityTime("Doha", "Qatar", "Asia/Qatar"),
            CityTime("Kuwait City", "Kuwait", "Asia/Kuwait"),
            CityTime("Manama", "Bahrain", "Asia/Bahrain"),
            CityTime("Muscat", "Oman", "Asia/Muscat"),
            CityTime("Sana'a", "Yemen", "Asia/Aden"),
            CityTime("Amman", "Jordan", "Asia/Amman"),
            CityTime("Beirut", "Lebanon", "Asia/Beirut"),
            CityTime("Damascus", "Syria", "Asia/Damascus"),
            CityTime("Jerusalem", "Israel", "Asia/Jerusalem"),
            CityTime("Tel Aviv", "Israel", "Asia/Jerusalem"),
            CityTime("Ankara", "Thổ Nhĩ Kỳ", "Europe/Istanbul"),
            CityTime("Istanbul", "Thổ Nhĩ Kỳ", "Europe/Istanbul"),
            CityTime("Tashkent", "Uzbekistan", "Asia/Tashkent"),
            CityTime("Almaty", "Kazakhstan", "Asia/Almaty"),
            CityTime("Astana", "Kazakhstan", "Asia/Almaty"),
            CityTime("Bishkek", "Kyrgyzstan", "Asia/Bishkek"),
            CityTime("Dushanbe", "Tajikistan", "Asia/Dushanbe"),
            CityTime("Ashgabat", "Turkmenistan", "Asia/Ashgabat"),
            CityTime("Ulaanbaatar", "Mongolia", "Asia/Ulaanbaatar"),
            CityTime("Pyongyang", "Triều Tiên", "Asia/Pyongyang"),
            CityTime("Thimphu", "Bhutan", "Asia/Thimphu"),
            CityTime("Malé", "Maldives", "Indian/Maldives"),
            CityTime("Bandar Seri Begawan", "Brunei", "Asia/Brunei"),
            CityTime("Dili", "Timor-Leste", "Asia/Dili"),
            // Europe
            CityTime("London", "Anh", "Europe/London"),
            CityTime("Dublin", "Ireland", "Europe/Dublin"),
            CityTime("Paris", "Pháp", "Europe/Paris"),
            CityTime("Berlin", "Đức", "Europe/Berlin"),
            CityTime("Munich", "Đức", "Europe/Berlin"),
            CityTime("Rome", "Ý", "Europe/Rome"),
            CityTime("Milan", "Ý", "Europe/Rome"),
            CityTime("Madrid", "Tây Ban Nha", "Europe/Madrid"),
            CityTime("Barcelona", "Tây Ban Nha", "Europe/Madrid"),
            CityTime("Lisbon", "Bồ Đào Nha", "Europe/Lisbon"),
            CityTime("Amsterdam", "Hà Lan", "Europe/Amsterdam"),
            CityTime("Brussels", "Bỉ", "Europe/Brussels"),
            CityTime("Luxembourg", "Luxembourg", "Europe/Luxembourg"),
            CityTime("Zurich", "Thụy Sĩ", "Europe/Zurich"),
            CityTime("Geneva", "Thụy Sĩ", "Europe/Zurich"),
            CityTime("Vienna", "Áo", "Europe/Vienna"),
            CityTime("Prague", "Séc", "Europe/Prague"),
            CityTime("Budapest", "Hungary", "Europe/Budapest"),
            CityTime("Warsaw", "Ba Lan", "Europe/Warsaw"),
            CityTime("Stockholm", "Thụy Điển", "Europe/Stockholm"),
            CityTime("Oslo", "Na Uy", "Europe/Oslo"),
            CityTime("Copenhagen", "Đan Mạch", "Europe/Copenhagen"),
            CityTime("Helsinki", "Phần Lan", "Europe/Helsinki"),
            CityTime("Reykjavik", "Iceland", "Atlantic/Reykjavik"),
            CityTime("Athens", "Hy Lạp", "Europe/Athens"),
            CityTime("Bucharest", "Romania", "Europe/Bucharest"),
            CityTime("Sofia", "Bulgaria", "Europe/Sofia"),
            CityTime("Belgrade", "Serbia", "Europe/Belgrade"),
            CityTime("Zagreb", "Croatia", "Europe/Zagreb"),
            CityTime("Ljubljana", "Slovenia", "Europe/Ljubljana"),
            CityTime("Sarajevo", "Bosnia", "Europe/Sarajevo"),
            CityTime("Skopje", "Bắc Macedonia", "Europe/Skopje"),
            CityTime("Podgorica", "Montenegro", "Europe/Podgorica"),
            CityTime("Tirana", "Albania", "Europe/Tirane"),
            CityTime("Pristina", "Kosovo", "Europe/Belgrade"),
            CityTime("Kyiv", "Ukraine", "Europe/Kyiv"),
            CityTime("Minsk", "Belarus", "Europe/Minsk"),
            CityTime("Moscow", "Nga", "Europe/Moscow"),
            CityTime("Saint Petersburg", "Nga", "Europe/Moscow"),
            CityTime("Vladivostok", "Nga", "Asia/Vladivostok"),
            CityTime("Chisinau", "Moldova", "Europe/Chisinau"),
            CityTime("Vilnius", "Lithuania", "Europe/Vilnius"),
            CityTime("Riga", "Latvia", "Europe/Riga"),
            CityTime("Tallinn", "Estonia", "Europe/Tallinn"),
            CityTime("Valletta", "Malta", "Europe/Malta"),
            CityTime("Nicosia", "Síp", "Asia/Nicosia"),
            CityTime("Monaco", "Monaco", "Europe/Monaco"),
            CityTime("Andorra la Vella", "Andorra", "Europe/Andorra"),
            CityTime("San Marino", "San Marino", "Europe/San_Marino"),
            CityTime("Vatican City", "Vatican", "Europe/Vatican"),
            CityTime("Vaduz", "Liechtenstein", "Europe/Vaduz"),
            // Americas
            CityTime("New York", "Mỹ", "America/New_York"),
            CityTime("Washington D.C.", "Mỹ", "America/New_York"),
            CityTime("Chicago", "Mỹ", "America/Chicago"),
            CityTime("Denver", "Mỹ", "America/Denver"),
            CityTime("Los Angeles", "Mỹ", "America/Los_Angeles"),
            CityTime("San Francisco", "Mỹ", "America/Los_Angeles"),
            CityTime("Seattle", "Mỹ", "America/Los_Angeles"),
            CityTime("Miami", "Mỹ", "America/New_York"),
            CityTime("Houston", "Mỹ", "America/Chicago"),
            CityTime("Honolulu", "Mỹ (Hawaii)", "Pacific/Honolulu"),
            CityTime("Anchorage", "Mỹ (Alaska)", "America/Anchorage"),
            CityTime("Toronto", "Canada", "America/Toronto"),
            CityTime("Vancouver", "Canada", "America/Vancouver"),
            CityTime("Montreal", "Canada", "America/Toronto"),
            CityTime("Ottawa", "Canada", "America/Toronto"),
            CityTime("Mexico City", "Mexico", "America/Mexico_City"),
            CityTime("Cancún", "Mexico", "America/Cancun"),
            CityTime("Havana", "Cuba", "America/Havana"),
            CityTime("Kingston", "Jamaica", "America/Jamaica"),
            CityTime("Port-au-Prince", "Haiti", "America/Port-au-Prince"),
            CityTime("Santo Domingo", "Dominican Republic", "America/Santo_Domingo"),
            CityTime("San Juan", "Puerto Rico", "America/Puerto_Rico"),
            CityTime("Panama City", "Panama", "America/Panama"),
            CityTime("San José", "Costa Rica", "America/Costa_Rica"),
            CityTime("Managua", "Nicaragua", "America/Managua"),
            CityTime("Tegucigalpa", "Honduras", "America/Tegucigalpa"),
            CityTime("San Salvador", "El Salvador", "America/El_Salvador"),
            CityTime("Guatemala City", "Guatemala", "America/Guatemala"),
            CityTime("Belmopan", "Belize", "America/Belize"),
            CityTime("Bogotá", "Colombia", "America/Bogota"),
            CityTime("Caracas", "Venezuela", "America/Caracas"),
            CityTime("Quito", "Ecuador", "America/Guayaquil"),
            CityTime("Lima", "Peru", "America/Lima"),
            CityTime("La Paz", "Bolivia", "America/La_Paz"),
            CityTime("Santiago", "Chile", "America/Santiago"),
            CityTime("Buenos Aires", "Argentina", "America/Argentina/Buenos_Aires"),
            CityTime("Montevideo", "Uruguay", "America/Montevideo"),
            CityTime("Asunción", "Paraguay", "America/Asuncion"),
            CityTime("Brasília", "Brazil", "America/Sao_Paulo"),
            CityTime("São Paulo", "Brazil", "America/Sao_Paulo"),
            CityTime("Rio de Janeiro", "Brazil", "America/Sao_Paulo"),
            CityTime("Georgetown", "Guyana", "America/Guyana"),
            CityTime("Paramaribo", "Suriname", "America/Paramaribo"),
            CityTime("Cayenne", "French Guiana", "America/Cayenne"),
            // Africa
            CityTime("Cairo", "Ai Cập", "Africa/Cairo"),
            CityTime("Tripoli", "Libya", "Africa/Tripoli"),
            CityTime("Tunis", "Tunisia", "Africa/Tunis"),
            CityTime("Algiers", "Algeria", "Africa/Algiers"),
            CityTime("Rabat", "Morocco", "Africa/Casablanca"),
            CityTime("Casablanca", "Morocco", "Africa/Casablanca"),
            CityTime("Nouakchott", "Mauritania", "Africa/Nouakchott"),
            CityTime("Dakar", "Senegal", "Africa/Dakar"),
            CityTime("Bamako", "Mali", "Africa/Bamako"),
            CityTime("Ouagadougou", "Burkina Faso", "Africa/Ouagadougou"),
            CityTime("Niamey", "Niger", "Africa/Niamey"),
            CityTime("Abuja", "Nigeria", "Africa/Lagos"),
            CityTime("Lagos", "Nigeria", "Africa/Lagos"),
            CityTime("Accra", "Ghana", "Africa/Accra"),
            CityTime("Abidjan", "Côte d'Ivoire", "Africa/Abidjan"),
            CityTime("Monrovia", "Liberia", "Africa/Monrovia"),
            CityTime("Freetown", "Sierra Leone", "Africa/Freetown"),
            CityTime("Conakry", "Guinea", "Africa/Conakry"),
            CityTime("Bissau", "Guinea-Bissau", "Africa/Bissau"),
            CityTime("Banjul", "Gambia", "Africa/Banjul"),
            CityTime("Lomé", "Togo", "Africa/Lome"),
            CityTime("Cotonou", "Benin", "Africa/Porto-Novo"),
            CityTime("Yaoundé", "Cameroon", "Africa/Douala"),
            CityTime("Libreville", "Gabon", "Africa/Libreville"),
            CityTime("Brazzaville", "Congo", "Africa/Brazzaville"),
            CityTime("Kinshasa", "DR Congo", "Africa/Kinshasa"),
            CityTime("Bangui", "CAR", "Africa/Bangui"),
            CityTime("N'Djamena", "Chad", "Africa/Ndjamena"),
            CityTime("Khartoum", "Sudan", "Africa/Khartoum"),
            CityTime("Juba", "South Sudan", "Africa/Juba"),
            CityTime("Addis Ababa", "Ethiopia", "Africa/Addis_Ababa"),
            CityTime("Asmara", "Eritrea", "Africa/Asmara"),
            CityTime("Djibouti", "Djibouti", "Africa/Djibouti"),
            CityTime("Mogadishu", "Somalia", "Africa/Mogadishu"),
            CityTime("Nairobi", "Kenya", "Africa/Nairobi"),
            CityTime("Kampala", "Uganda", "Africa/Kampala"),
            CityTime("Kigali", "Rwanda", "Africa/Kigali"),
            CityTime("Bujumbura", "Burundi", "Africa/Bujumbura"),
            CityTime("Dodoma", "Tanzania", "Africa/Dar_es_Salaam"),
            CityTime("Dar es Salaam", "Tanzania", "Africa/Dar_es_Salaam"),
            CityTime("Maputo", "Mozambique", "Africa/Maputo"),
            CityTime("Lilongwe", "Malawi", "Africa/Blantyre"),
            CityTime("Lusaka", "Zambia", "Africa/Lusaka"),
            CityTime("Harare", "Zimbabwe", "Africa/Harare"),
            CityTime("Gaborone", "Botswana", "Africa/Gaborone"),
            CityTime("Windhoek", "Namibia", "Africa/Windhoek"),
            CityTime("Pretoria", "Nam Phi", "Africa/Johannesburg"),
            CityTime("Johannesburg", "Nam Phi", "Africa/Johannesburg"),
            CityTime("Cape Town", "Nam Phi", "Africa/Johannesburg"),
            CityTime("Maseru", "Lesotho", "Africa/Maseru"),
            CityTime("Mbabane", "Eswatini", "Africa/Mbabane"),
            CityTime("Antananarivo", "Madagascar", "Indian/Antananarivo"),
            CityTime("Port Louis", "Mauritius", "Indian/Mauritius"),
            CityTime("Victoria", "Seychelles", "Indian/Mahe"),
            CityTime("Moroni", "Comoros", "Indian/Comoro"),
            // Oceania
            CityTime("Sydney", "Úc", "Australia/Sydney"),
            CityTime("Melbourne", "Úc", "Australia/Melbourne"),
            CityTime("Brisbane", "Úc", "Australia/Brisbane"),
            CityTime("Perth", "Úc", "Australia/Perth"),
            CityTime("Adelaide", "Úc", "Australia/Adelaide"),
            CityTime("Canberra", "Úc", "Australia/Sydney"),
            CityTime("Auckland", "New Zealand", "Pacific/Auckland"),
            CityTime("Wellington", "New Zealand", "Pacific/Auckland"),
            CityTime("Suva", "Fiji", "Pacific/Fiji"),
            CityTime("Port Moresby", "Papua New Guinea", "Pacific/Port_Moresby"),
            CityTime("Nouméa", "New Caledonia", "Pacific/Noumea"),
            CityTime("Papeete", "French Polynesia", "Pacific/Tahiti"),
            CityTime("Apia", "Samoa", "Pacific/Apia"),
            CityTime("Nuku'alofa", "Tonga", "Pacific/Tongatapu"),
            CityTime("Port Vila", "Vanuatu", "Pacific/Efate"),
            CityTime("Honiara", "Solomon Islands", "Pacific/Guadalcanal"),
            CityTime("Tarawa", "Kiribati", "Pacific/Tarawa"),
            CityTime("Majuro", "Marshall Islands", "Pacific/Majuro"),
            CityTime("Palikir", "Micronesia", "Pacific/Pohnpei"),
            CityTime("Ngerulmud", "Palau", "Pacific/Palau"),
            CityTime("Yaren", "Nauru", "Pacific/Nauru"),
            CityTime("Funafuti", "Tuvalu", "Pacific/Funafuti"),
            CityTime("Hagåtña", "Guam", "Pacific/Guam")
        )

        val seen = curated.map { it.timeZoneId to it.cityName }.toMutableSet()
        val result = curated.toMutableList()

        // Bổ sung mọi TimeZone hệ thống còn thiếu (tên zone dạng Area/City)
        TimeZone.getAvailableIDs().forEach { id ->
            if (id.startsWith("Etc/") || id.startsWith("SystemV") || id.contains("GMT")) return@forEach
            val city = id.substringAfterLast('/').replace('_', ' ')
            val region = id.substringBefore('/').replace('_', ' ')
            val key = id to city
            if (seen.none { it.first == id }) {
                seen.add(key)
                result.add(CityTime(city, region, id))
            }
        }
        return result
    }

    override fun onResume() {
        super.onResume()
        try { binding.root.alpha = 1f } catch (_: Exception) {}
        try { binding.curvedNav.selectIndex(1, animate = false) } catch (_: Exception) {}
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }
}

class WorldClockAdapter(private var cities: MutableList<CityTime>) :
    RecyclerView.Adapter<WorldClockAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCity: TextView = view.findViewById(R.id.tvCity)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    fun update(newList: List<CityTime>) {
        cities.clear()
        cities.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_world_clock, parent, false)
        return VH(v)
    }

    override fun getItemCount() = cities.size

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(WorldClockActivity.PAYLOAD_TIME)) {
            bindTime(holder, cities[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = cities[position]
        holder.tvCity.text = "${item.cityName} · ${item.country}"
        bindTime(holder, item)
    }

    private fun bindTime(holder: VH, item: CityTime) {
        val tz = TimeZone.getTimeZone(item.timeZoneId)
        val now = Date()
        val use24 = try {
            AppSettings.isUse24h(holder.itemView.context)
        } catch (_: Exception) { true }
        val timeFmt = SimpleDateFormat(if (use24) "HH:mm:ss" else "hh:mm:ss a", Locale.getDefault()).apply {
            timeZone = tz
        }
        val dateFmt = SimpleDateFormat("EEE, dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = tz
        }
        val offsetMs = tz.getOffset(now.time)
        val offsetH = offsetMs / 3_600_000
        val offsetM = kotlin.math.abs((offsetMs / 60_000) % 60)
        val gmt = if (offsetM == 0) "GMT%+d".format(offsetH) else "GMT%+d:%02d".format(offsetH, offsetM)
        holder.tvDate.text = "${dateFmt.format(now)} · $gmt"
        holder.tvTime.text = timeFmt.format(now)
    }

}
