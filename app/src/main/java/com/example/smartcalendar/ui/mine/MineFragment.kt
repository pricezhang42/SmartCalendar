package com.example.smartcalendar.ui.mine

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.LocalCalendar
import com.example.smartcalendar.data.repository.LocalCalendarRepository
import com.example.smartcalendar.databinding.FragmentMineBinding
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Calendar management fragment with CRUD operations.
 */
class MineFragment : Fragment() {

    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: LocalCalendarRepository
    private lateinit var adapter: CalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalCalendarRepository.getInstance()

        setupRecyclerView()
        binding.addCalendarButton.setOnClickListener { showAddCalendarDialog() }
    }

    private fun setupRecyclerView() {
        adapter = CalendarAdapter(
            onEditClick = { calendar -> showEditCalendarDialog(calendar) },
            onDeleteClick = { calendar -> confirmDeleteCalendar(calendar) }
        )
        binding.calendarsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.calendarsRecyclerView.adapter = adapter
        refreshList()
    }

    private fun refreshList() {
        lifecycleScope.launch {
            adapter.submitList(repository.getCalendars())
        }
    }

    private fun showAddCalendarDialog() {
        showCalendarDialog(null)
    }

    private fun showEditCalendarDialog(calendar: LocalCalendar) {
        showCalendarDialog(calendar)
    }

    private fun showCalendarDialog(existingCalendar: LocalCalendar?) {
        val context = requireContext()
        val isEdit = existingCalendar != null

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(context).apply {
            hint = getString(R.string.calendar_name)
            setText(existingCalendar?.name ?: "")
        }
        layout.addView(nameInput)

        // Color selection
        val colorLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 0)
        }

        var selectedColor = existingCalendar?.color ?: LocalCalendar.COLOR_BLUE
        val colorViews = mutableListOf<View>()

        LocalCalendar.DEFAULT_COLORS.forEach { color ->
            val colorView = View(context).apply {
                val size = (40 * resources.displayMetrics.density).toInt()
                val margin = (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == selectedColor) {
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
                    }
                }
                setOnClickListener {
                    selectedColor = color
                    colorViews.forEach { v ->
                        (v.background as? GradientDrawable)?.setStroke(0, Color.TRANSPARENT)
                    }
                    (background as? GradientDrawable)?.setStroke(
                        (3 * resources.displayMetrics.density).toInt(), Color.BLACK
                    )
                }
            }
            colorViews.add(colorView)
            colorLayout.addView(colorView)
        }
        layout.addView(colorLayout)

        AlertDialog.Builder(context)
            .setTitle(if (isEdit) R.string.edit_calendar else R.string.add_calendar)
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    if (isEdit) {
                        repository.updateCalendar(existingCalendar!!.copy(name = name, color = selectedColor))
                    } else {
                        repository.addCalendar(LocalCalendar(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            color = selectedColor
                        ))
                    }
                    refreshList()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmDeleteCalendar(calendar: LocalCalendar) {
        if (calendar.isDefault) {
            Toast.makeText(context, "Cannot delete default calendars", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Calendar")
            .setMessage("Delete \"${calendar.name}\" and all its events?")
            .setPositiveButton("DELETE") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteCalendar(calendar.id)
                    refreshList()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // =============== Adapter ===============

    inner class CalendarAdapter(
        private val onEditClick: (LocalCalendar) -> Unit,
        private val onDeleteClick: (LocalCalendar) -> Unit
    ) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

        private var calendars = listOf<LocalCalendar>()

        fun submitList(list: List<LocalCalendar>) {
            calendars = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(calendars[position])
        }

        override fun getItemCount() = calendars.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val text1 = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            private val text2 = itemView.findViewById<android.widget.TextView>(android.R.id.text2)

            fun bind(calendar: LocalCalendar) {
                text1.text = calendar.name
                text1.setTextColor(calendar.color)
                text2.text = if (calendar.isDefault) "Default" else "Custom"
                text2.setTextColor(Color.GRAY)

                itemView.setOnClickListener { onEditClick(calendar) }
                itemView.setOnLongClickListener {
                    onDeleteClick(calendar)
                    true
                }
            }
        }
    }
}
