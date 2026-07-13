package com.dientieuthu.app

import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import com.dientieuthu.app.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Định nghĩa 1 loại thiết bị:
 * - powerKw: công suất tiêu thụ trung bình (kW) của thiết bị phổ biến trên thị trường VN
 * - fixedKwhPerDay: nếu > 0 thì thiết bị chạy 24/24 (VD tủ lạnh), tính theo kWh/ngày,
 *   không cần nhập giờ.
 */
data class DeviceType(
    val name: String,
    val powerKw: Double,
    val fixedKwhPerDay: Double = 0.0
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ==== ĐỊNH MỨC CÔNG SUẤT TRUNG BÌNH (thị trường VN) ====
    // Máy lạnh: đã gồm quạt dàn lạnh, hệ số máy nén chạy ~80% thời gian
    //   1HP  ≈ 746W  -> thực tế ~0.80 kWh/giờ
    //   1.5HP≈ 1119W -> thực tế ~1.20 kWh/giờ
    //   2HP  ≈ 1492W -> thực tế ~1.60 kWh/giờ
    // Đèn LED: bóng LED phòng ngủ chuẩn khách sạn 2 sao ~15W/bóng
    // Quạt đứng/treo tường: ~55W
    // Bình nóng lạnh gián tiếp 20-30L phổ biến: ~2.5kW
    // Tủ lạnh 150-250L: ~1.2 kWh/ngày (chạy 24/24)
    // TV LED 43-50 inch: ~80W
    private val deviceTypes = listOf(
        DeviceType("Máy lạnh 1 HP (ngựa)", 0.80),
        DeviceType("Máy lạnh 1.5 HP", 1.20),
        DeviceType("Máy lạnh 2 HP", 1.60),
        DeviceType("Đèn LED (15W/bóng)", 0.015),
        DeviceType("Quạt (55W)", 0.055),
        DeviceType("Bình nóng lạnh (2.5kW)", 2.5),
        DeviceType("TV (80W)", 0.08),
        DeviceType("Tủ lạnh (1.2 kWh/ngày)", 0.0, fixedKwhPerDay = 1.2)
    )

    // Giá điện sinh hoạt EVN 6 bậc (đ/kWh) - tham khảo, áp dụng từ 5/2025
    private val tierLimits = intArrayOf(50, 50, 100, 100, 100, Int.MAX_VALUE)
    private val tierPrices = doubleArrayOf(1984.0, 2050.0, 2380.0, 2998.0, 3350.0, 3460.0)

    private val qtyInputs = mutableListOf<EditText>()
    private val hourInputs = mutableListOf<EditText?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildDeviceRows()

        binding.btnCalc.setOnClickListener { calculate() }
    }

    /** Tạo động các hàng nhập liệu cho từng thiết bị */
    private fun buildDeviceRows() {
        for (device in deviceTypes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val label = TextView(this).apply {
                text = device.name
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }

            val etQty = EditText(this).apply {
                hint = "0"
                inputType = InputType.TYPE_CLASS_NUMBER
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(label)
            row.addView(etQty)
            qtyInputs.add(etQty)

            if (device.fixedKwhPerDay > 0) {
                // Thiết bị chạy 24/24: không cần nhập giờ
                val fixedLabel = TextView(this).apply {
                    text = "24/24"
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(fixedLabel)
                hourInputs.add(null)
            } else {
                val etHours = EditText(this).apply {
                    hint = "0"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(etHours)
                hourInputs.add(etHours)
            }

            binding.deviceContainer.addView(row)
        }
    }

    private fun num(et: EditText?): Double =
        et?.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

    private fun calculate() {
        val days = if (binding.rbQuarter.isChecked) 90 else 30
        val periodName = if (days == 90) "quý" else "tháng"

        val sb = StringBuilder()
        var totalKwh = 0.0
        sb.append("📊 CHI TIẾT ƯỚC TÍNH (${days} ngày)\n")
        sb.append("──────────────────────────\n")

        for (i in deviceTypes.indices) {
            val d = deviceTypes[i]
            val qty = num(qtyInputs[i])
            if (qty <= 0) continue

            val kwh: Double
            if (d.fixedKwhPerDay > 0) {
                kwh = qty * d.fixedKwhPerDay * days
                sb.append("• ${d.name} ×${qty.toInt()}: ${fmt(kwh)} kWh\n")
            } else {
                val hours = num(hourInputs[i])
                if (hours <= 0) continue
                if (hours > 24) {
                    binding.tvResult.text = "⚠️ Giờ/ngày của \"${d.name}\" không được vượt quá 24!"
                    binding.tvResult.visibility = TextView.VISIBLE
                    return
                }
                kwh = qty * d.powerKw * hours * days
                sb.append("• ${d.name} ×${qty.toInt()} × ${fmt(hours)}h/ngày: ${fmt(kwh)} kWh\n")
            }
            totalKwh += kwh
        }

        // Thiết bị khác
        val otherWatt = num(binding.etOtherWatt)
        val otherQty = num(binding.etOtherQty)
        val otherHours = num(binding.etOtherHours)
        if (otherWatt > 0 && otherQty > 0 && otherHours > 0) {
            val kwh = otherQty * (otherWatt / 1000.0) * otherHours * days
            sb.append("• Thiết bị khác ${fmt(otherWatt)}W ×${otherQty.toInt()} × ${fmt(otherHours)}h: ${fmt(kwh)} kWh\n")
            totalKwh += kwh
        }

        if (totalKwh <= 0) {
            binding.tvResult.text = "⚠️ Vui lòng nhập ít nhất 1 thiết bị (số lượng và giờ sử dụng)."
            binding.tvResult.visibility = TextView.VISIBLE
            return
        }

        sb.append("──────────────────────────\n")
        sb.append("🔢 TỔNG ƯỚC TÍNH: ${fmt(totalKwh)} kWh/$periodName\n")
        sb.append("💰 Tiền điện ước tính: ${money(costOf(totalKwh, days))}\n\n")

        // So sánh với thực tế
        val actual = num(binding.etActualKwh)
        if (actual > 0) {
            val diff = actual - totalKwh
            val pct = diff / totalKwh * 100.0
            sb.append("📋 SỐ ĐIỆN THỰC TẾ: ${fmt(actual)} kWh\n")
            sb.append("💰 Tiền điện thực tế: ${money(costOf(actual, days))}\n")
            sb.append("Chênh lệch: ${if (diff >= 0) "+" else ""}${fmt(diff)} kWh (${if (pct >= 0) "+" else ""}${fmt(pct)}%)\n\n")

            sb.append("🎯 KẾT LUẬN:\n")
            when {
                pct > 30 -> sb.append("🔴 DÙNG NHIỀU BẤT THƯỜNG! Thực tế cao hơn ước tính ${fmt(pct)}%.\n" +
                        "→ Kiểm tra: máy lạnh bẩn/hết gas, rò điện, thiết bị chưa khai báo, dây điện cũ.")
                pct > 15 -> sb.append("🟠 DÙNG NHIỀU hơn mức tính toán (${fmt(pct)}%).\n" +
                        "→ Nên vệ sinh máy lạnh, cài 26-27°C, rút phích thiết bị chờ (standby).")
                pct >= -15 -> sb.append("🟢 BÌNH THƯỜNG. Số điện thực tế khớp với thiết bị đang dùng (chênh ${fmt(pct)}%).")
                else -> sb.append("🔵 DÙNG ÍT hơn ước tính (${fmt(pct)}%).\n" +
                        "→ Có thể thời gian sử dụng thực tế thấp hơn khai báo, hoặc thiết bị tiết kiệm điện (inverter).")
            }

            // So với hộ gia đình trung bình VN (~200 kWh/tháng)
            val avgVN = 200.0 * days / 30.0
            sb.append("\n\n📈 So với hộ gia đình VN trung bình (~${fmt(avgVN)} kWh/$periodName): ")
            sb.append(
                when {
                    actual > avgVN * 1.2 -> "cao hơn trung bình."
                    actual < avgVN * 0.8 -> "thấp hơn trung bình."
                    else -> "tương đương trung bình."
                }
            )
        } else {
            sb.append("💡 Nhập số kWh thực tế trên hóa đơn để so sánh và kết luận dùng nhiều hay ít.")
        }

        binding.tvResult.text = sb.toString()
        binding.tvResult.visibility = TextView.VISIBLE
    }

    /** Tính tiền điện theo bậc thang EVN. Nếu tính theo quý thì chia về tháng rồi nhân 3. */
    private fun costOf(kwh: Double, days: Int): Double {
        val months = days / 30.0
        var remain = kwh / months
        var cost = 0.0
        for (i in tierLimits.indices) {
            if (remain <= 0) break
            val used = minOf(remain, tierLimits[i].toDouble())
            cost += used * tierPrices[i]
            remain -= used
        }
        return cost * months
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.1f", v)

    private fun money(v: Double): String =
        String.format(Locale.GERMANY, "%,.0f", v) + " đ (tham khảo, chưa VAT)"
}
