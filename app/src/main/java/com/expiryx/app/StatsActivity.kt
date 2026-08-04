package com.expiryx.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.expiryx.app.databinding.ActivityStatsBinding
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import java.util.Locale

/**
 * FUNCTIONALITY: Renders a comprehensive analytics dashboard featuring waste charts, 
 * lifecycle metrics, brand performance, and user efficiency KPIs.
 * USE OF DATA: Observes a 'StatsUiState' data object containing aggregated 'Float' and 
 * 'Int' values derived from the full product and history datasets.
 * USE OF CODE STRUCTURES: Extends 'ThemedAppCompatActivity'; employs the Observer 
 * pattern to reactively update complex chart and list components upon data emission.
 */
class StatsActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    private val viewModel: StatsViewModel by viewModels {
        StatsViewModelFactory((application as ProductApplication).repository)
    }

    private val brandsConsumedAdapter = StatBrandAdapter()
    private val brandsWastedAdapter = StatBrandAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecyclerViews()
        setupTimeRangeChips()
        setupBottomNav()
        setupAccountCard()
        setupCharts()
        observeStats()
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            insets
        }
    }

    private fun setupRecyclerViews() {
        binding.recyclerBrandsConsumed.layoutManager = LinearLayoutManager(this)
        binding.recyclerBrandsConsumed.adapter = brandsConsumedAdapter
        binding.recyclerBrandsWasted.layoutManager = LinearLayoutManager(this)
        binding.recyclerBrandsWasted.adapter = brandsWastedAdapter
    }

    /**
     * FUNCTIONALITY: Configures selection listeners for time-range filtering chips.
     * USE OF DATA: Maps UI components to 'TimeRange' enum values.
     * USE OF CODE STRUCTURES: Iterates over a 'Map' to attach click listeners to multiple views.
     */
    private fun setupTimeRangeChips() {
        val chipMap = mapOf(
            binding.chip7d to TimeRange.DAYS_7,
            binding.chip30d to TimeRange.DAYS_30,
            binding.chip90d to TimeRange.DAYS_90,
            binding.chip1y to TimeRange.YEAR,
            binding.chipAll to TimeRange.ALL,
        )
        // CODE STRUCTURE: Iteration structure for batching listener setup
        chipMap.forEach { (chip, range) ->
            chip.setOnClickListener {
                viewModel.setTimeRange(range)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, binding.bottomNav.bottomNavigationView, R.id.nav_stats)
    }

    private fun setupAccountCard() {
        binding.accountStatsCard.setOnClickListener {
            val user = AccountManager.getCurrentUser()
            if (user != null) {
                startActivity(Intent(this, AccountActivity::class.java))
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("force_login", true)
                startActivity(intent)
            }
        }
    }

    private fun setupCharts() {
        stylePieChart(binding.chartLifecycle)
        styleBarChart(binding.chartActivity)
    }

    private fun stylePieChart(chart: PieChart) {
        chart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(true)
            setEntryLabelColor(resolveChartTextColor())
            setEntryLabelTextSize(11f)
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            holeRadius = 45f
            transparentCircleRadius = 50f
            setDrawCenterText(true)
            setCenterTextSize(14f)
            setCenterTextColor(resolveChartTextColor())
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.isWordWrapEnabled = true
            legend.textColor = resolveChartTextColor()
            legend.textSize = 11f
            setNoDataText(getString(R.string.stats_no_activity_period))
            setNoDataTextColor(resolveChartTextColor())
        }
    }

    private fun styleBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(false)
            axisRight.isEnabled = false
            axisLeft.textColor = resolveChartTextColor()
            axisLeft.axisMinimum = 0f
            axisLeft.granularity = 1f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.textColor = resolveChartTextColor()
            xAxis.setDrawGridLines(false)
            legend.textColor = resolveChartTextColor()
            legend.textSize = 11f
            setNoDataText(getString(R.string.stats_no_activity_period))
            setNoDataTextColor(resolveChartTextColor())
        }
    }

    private fun resolveChartTextColor(): Int {
        return ContextCompat.getColor(this, if (ThemeManager.isDarkMode(this)) R.color.white else R.color.black)
    }

    /**
     * FUNCTIONALITY: Binds the calculated statistical state to the view's various data visualizers.
     * USE OF DATA: Consumes 'StatsUiState' emission.
     * USE OF CODE STRUCTURES: Selection structure (if/else) handling global empty-state 
     * and sequential calls to specific binder methods.
     */
    private fun observeStats() {
        // CODE STRUCTURE: Observer pattern reacting to calculated analytics updates
        viewModel.statsState.observe(this) { state ->
            updateTimeRangeSelection(state.timeRange)
            val emptyState = binding.emptyStateLayout
            
            // CODE STRUCTURE: Selection structure for managing dashboard content visibility
            emptyState.root.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
            binding.statsContentLayout.visibility = if (state.isEmpty) View.GONE else View.VISIBLE
            
            if (state.isEmpty) {
                emptyState.emptyStateIcon.setImageResource(R.drawable.ic_stats_unfilled)
                emptyState.emptyStateTitle.text = getString(R.string.stats_empty_title)
                emptyState.emptyStateSubtitle.text = getString(R.string.stats_empty_subtitle)
                return@observe
            }

            bindKpiCards(state)
            bindSecondaryStats(state)
            bindLifecycleChart(state)
            bindActivityChart(state)
            bindExpiryBuckets(state)
            bindBrandLists(state)
            bindInsights(state)
            bindAccountCard(state)
        }
    }

    private fun updateTimeRangeSelection(range: TimeRange) {
        val chipId = when (range) {
            TimeRange.DAYS_7 -> binding.chip7d.id
            TimeRange.DAYS_30 -> binding.chip30d.id
            TimeRange.DAYS_90 -> binding.chip90d.id
            TimeRange.YEAR -> binding.chip1y.id
            TimeRange.ALL -> binding.chipAll.id
        }
        binding.chipGroupTimeRange.check(chipId)
    }

    private fun bindKpiCards(state: StatsUiState) {
        bindKpi(binding.kpiActive, getString(R.string.stats_kpi_active), state.activeItems.toString(),
            getString(R.string.stats_kpi_qty_sub, state.activeQuantity))
        bindKpi(binding.kpiConsumed, getString(R.string.stats_kpi_consumed), state.consumedCount.toString())
        bindKpi(binding.kpiExpired, getString(R.string.stats_kpi_expired), state.expiredCount.toString())
        bindKpi(binding.kpiWaste, getString(R.string.stats_kpi_waste_rate),
            String.format(Locale.getDefault(), "%.0f%%", state.wasteRate))
    }

    private fun bindKpi(
        kpiBinding: com.expiryx.app.databinding.ItemStatKpiCardBinding,
        label: String,
        value: String,
        sub: String? = null,
    ) {
        kpiBinding.kpiLabel.text = label
        kpiBinding.kpiValue.text = value
        if (sub != null) {
            kpiBinding.kpiSub.text = sub
            kpiBinding.kpiSub.visibility = View.VISIBLE
        } else {
            kpiBinding.kpiSub.visibility = View.GONE
        }
    }

    private fun bindSecondaryStats(state: StatsUiState) {
        val avgDays = state.avgDaysToUse?.let {
            String.format(Locale.getDefault(), "%.1f", it)
        } ?: getString(R.string.stats_not_available)

        binding.txtSecondaryStats.text = getString(
            R.string.stats_secondary_summary,
            state.deletedCount,
            state.itemsAddedInRange,
            avgDays,
            String.format(Locale.getDefault(), "%.0f%%", state.consumedBeforeExpiryPercent),
        )
    }

    /**
     * FUNCTIONALITY: Populates the Lifecycle PieChart with distribution data (Used vs Expired vs Deleted).
     * USE OF DATA: Converts Int lifecycle counts to 'PieEntry' objects.
     * USE OF CODE STRUCTURES: Functional 'filter' to remove empty entries and 'apply' 
     * for style configuration.
     */
    private fun bindLifecycleChart(state: StatsUiState) {
        val total = state.lifecycleUsed + state.lifecycleExpired + state.lifecycleDeleted
        val hasData = total > 0
        binding.chartLifecycle.visibility = if (hasData) View.VISIBLE else View.GONE
        binding.txtLifecycleEmpty.visibility = if (hasData) View.GONE else View.VISIBLE

        if (!hasData) return

        // DATA: Mapping lifecycle counts to PieChart entries
        val entries = listOf(
            PieEntry(state.lifecycleUsed.toFloat(), getString(R.string.stats_action_used)),
            PieEntry(state.lifecycleExpired.toFloat(), getString(R.string.stats_action_expired)),
            PieEntry(state.lifecycleDeleted.toFloat(), getString(R.string.stats_action_deleted)),
        ).filter { it.value > 0f }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(this@StatsActivity, R.color.green),
                ContextCompat.getColor(this@StatsActivity, R.color.red),
                ContextCompat.getColor(this@StatsActivity, R.color.gray),
            )
            sliceSpace = 2f
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            valueFormatter = PercentFormatter(binding.chartLifecycle)
        }

        binding.chartLifecycle.centerText = getString(R.string.stats_lifecycle_center, total)
        binding.chartLifecycle.data = PieData(dataSet)
        binding.chartLifecycle.invalidate()
    }

    /**
     * FUNCTIONALITY: Populates the Weekly activity BarChart with grouped trend data.
     * USE OF DATA: Transforms 'List<WeeklyActivity>' into four separate 'BarDataSet' instances.
     * USE OF CODE STRUCTURES: 'mapIndexed' iteration to generate X-axis coordinates 
     * and 'groupBars' to cluster multi-type data points.
     */
    private fun bindActivityChart(state: StatsUiState) {
        val weeks = state.weeklyActivity
        val hasData = weeks.any { it.added + it.used + it.expired + it.deleted > 0 }
        binding.chartActivity.visibility = if (hasData) View.VISIBLE else View.GONE
        binding.txtActivityEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        if (!hasData) return

        val labels = weeks.map { it.weekLabel }.toTypedArray()
        val groupSpace = 0.08f
        val barSpace = 0.02f
        val barWidth = 0.18f

        // USE OF CODE STRUCTURES: Functional mapping to build temporal trend entries
        val addedEntries = weeks.mapIndexed { i, w -> BarEntry(i.toFloat(), w.added.toFloat()) }
        val usedEntries = weeks.mapIndexed { i, w -> BarEntry(i.toFloat(), w.used.toFloat()) }
        val expiredEntries = weeks.mapIndexed { i, w -> BarEntry(i.toFloat(), w.expired.toFloat()) }
        val deletedEntries = weeks.mapIndexed { i, w -> BarEntry(i.toFloat(), w.deleted.toFloat()) }

        val addedSet = barDataSet(addedEntries, getString(R.string.stats_legend_added), R.color.blue)
        val usedSet = barDataSet(usedEntries, getString(R.string.stats_action_used), R.color.green)
        val expiredSet = barDataSet(expiredEntries, getString(R.string.stats_action_expired), R.color.red)
        val deletedSet = barDataSet(deletedEntries, getString(R.string.stats_action_deleted), R.color.gray)

        val data = BarData(addedSet, usedSet, expiredSet, deletedSet).apply {
            this.barWidth = barWidth
        }

        binding.chartActivity.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            this.data = data
            xAxis.axisMinimum = -0.5f
            xAxis.axisMaximum = labels.size - 0.5f
            // CODE STRUCTURE: API call for clustered bar visualization
            groupBars(-0.5f, groupSpace, barSpace)
            invalidate()
        }
    }

    private fun barDataSet(entries: List<BarEntry>, label: String, colorRes: Int): BarDataSet {
        return BarDataSet(entries, label).apply {
            color = ContextCompat.getColor(this@StatsActivity, colorRes)
            setDrawValues(false)
        }
    }

    /**
     * FUNCTIONALITY: Dynamically builds a vertical bar chart of products bucketed by expiry status.
     * USE OF DATA: Iterates over 'List<ExpiryBucketStat>', creating views for each.
     * USE OF CODE STRUCTURES: 'forEach' iteration over data triggers dynamic layout inflation.
     */
    private fun bindExpiryBuckets(state: StatsUiState) {
        binding.expiryBucketsContainer.removeAllViews()
        val buckets = state.expiryBuckets
        binding.txtExpiryEmpty.visibility = if (buckets.isEmpty()) View.VISIBLE else View.GONE
        binding.expiryBucketsContainer.visibility = if (buckets.isEmpty()) View.GONE else View.VISIBLE
        if (buckets.isEmpty()) return

        val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)
        val inflater = LayoutInflater.from(this)
        // CODE STRUCTURE: Iterative construction of the bucket comparison chart
        buckets.forEach { bucket ->
            val row = inflater.inflate(R.layout.view_expiry_bucket_bar, binding.expiryBucketsContainer, false)
            row.findViewById<TextView>(R.id.txtBucketLabel).text = bucket.label
            row.findViewById<TextView>(R.id.txtBucketCount).text = bucket.count.toString()
            val progress = row.findViewById<ProgressBar>(R.id.bucketProgress)
            progress.max = maxCount
            progress.progress = bucket.count
            progress.progressTintList = ContextCompat.getColorStateList(this, bucket.colorRes)
            binding.expiryBucketsContainer.addView(row)
        }
    }

    private fun bindBrandLists(state: StatsUiState) {
        brandsConsumedAdapter.submitList(state.topBrandsConsumed)
        brandsWastedAdapter.submitList(state.topBrandsWasted)
        binding.txtBrandsConsumedEmpty.visibility =
            if (state.topBrandsConsumed.isEmpty()) View.VISIBLE else View.GONE
        binding.txtBrandsWastedEmpty.visibility =
            if (state.topBrandsWasted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun bindInsights(state: StatsUiState) {
        binding.txtInsights.text = getString(
            R.string.stats_insights_summary,
            state.favoritesCount,
            String.format(Locale.getDefault(), "%.0f%%", state.barcodeScanRate),
            state.noExpiryCount,
            state.totalWeightG,
            state.totalVolumeMl,
        )
    }

    private fun bindAccountCard(state: StatsUiState) {
        val user = AccountManager.getCurrentUser()
        if (user != null) {
            binding.txtAccountStatsName.text = user.displayName ?: getString(R.string.stats_account_user)
            val syncStatus = if (Prefs.isSyncEnabled(this)) {
                getString(R.string.stats_sync_active)
            } else {
                getString(R.string.stats_sync_disabled)
            }
            binding.txtAccountStatsDetail.text = getString(
                R.string.stats_account_logged_in,
                user.email ?: "",
                syncStatus,
                state.activeItems,
                state.consumedCount + state.expiredCount + state.deletedCount,
            )
            if (user.photoUrl != null) {
                Glide.with(this).load(user.photoUrl).circleCrop().into(binding.imgAccountStats)
            } else {
                binding.imgAccountStats.setImageResource(R.drawable.ic_google_logo)
            }
        } else {
            binding.txtAccountStatsName.text = getString(R.string.stats_guest_title)
            binding.txtAccountStatsDetail.text = getString(R.string.stats_guest_subtitle)
            binding.imgAccountStats.setImageResource(R.drawable.ic_google_logo)
        }
    }
}