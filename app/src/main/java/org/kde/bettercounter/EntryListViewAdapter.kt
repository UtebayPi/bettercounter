package org.kde.bettercounter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import org.kde.bettercounter.boilerplate.DragAndSwipeTouchHelper
import org.kde.bettercounter.persistence.Counter
import org.kde.bettercounter.persistence.Interval
import java.util.*

class EntryListViewAdapter(
    private var activity: AppCompatActivity,
    private var viewModel: ViewModel,
    private var onItemClickListener: (pos : Int, counter : Counter) -> Unit
) : RecyclerView.Adapter<EntryViewHolder>(), DragAndSwipeTouchHelper.ListGesturesCallback
     {

    private val inflater: LayoutInflater = LayoutInflater.from(activity)
    private var counters: MutableList<String> = mutableListOf()

    override fun getItemCount(): Int = counters.size

    init {
        viewModel.observeNewCounter(activity, { newCounter ->
            counters.add(newCounter)
            activity.runOnUiThread {
                notifyItemInserted(counters.size - 1)
                viewModel.getCounter(newCounter)?.observe(activity) {
                    notifyItemChanged(counters.indexOf(it.name), Unit) // passing a second parameter disables the disappear+appear animation
                }
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = inflater.inflate(R.layout.fragment_entry, parent, false)
        val holder = EntryViewHolder(view, viewModel)
        view.setOnClickListener {
            val counter = holder.counter
            if (counter != null) {
                onItemClickListener(counters.indexOf(counter.name), counter)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val counter = viewModel.getCounter(counters[position])?.value
        if (counter != null) {
            holder.onBind(counter)
        }
    }

    fun removeItem(position: Int) {
        val name = counters.removeAt(position)
        notifyItemRemoved(position)
        viewModel.deleteCounter(name)
        viewModel.saveCounterOrder(counters)
    }

    fun editCounter(position: Int, newName: String, interval : Interval) {
        val oldName = counters[position]
        if (oldName != newName) {
            counters[position] = newName
            viewModel.renameCounter(oldName, newName)
            viewModel.saveCounterOrder(counters)
        }
        viewModel.setCounterInterval(newName, interval)
        notifyItemChanged(position)
    }

    override fun onMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(counters, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(counters, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        // Do not store individual movements, store the final result in `onDragEnd`
    }

    override fun onDragStart(viewHolder: RecyclerView.ViewHolder?) {
        //TODO: haptic feedback?
    }

    override fun onDragEnd(viewHolder: RecyclerView.ViewHolder?) {
        viewModel.saveCounterOrder(counters)
    }

    override fun onSwipe(position: Int) {
        val name = counters[position]
        val interval = viewModel.getCounterInterval(name)
        CounterSettingsDialogBuilder(activity, viewModel)
            .forExistingCounter(name, interval)
            .setOnSaveListener { newName, newInterval ->
                editCounter(position, newName, newInterval)
            }
            .setOnCancelListener { _, _ ->
                notifyItemChanged(position)
            }
            .setOnDeleteListener { _, _ ->
                removeItem(position);
            }
            .show()
    }

}
