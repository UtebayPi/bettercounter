package org.kde.bettercounter

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.bettercounter.boilerplate.AppDatabase
import org.kde.bettercounter.persistence.Counter
import org.kde.bettercounter.persistence.Repository

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val repo : Repository
    private val addCounterObservers = HashMap<LifecycleOwner, Observer<String>>()
    private val counterMap = HashMap<String, MutableLiveData<Counter>>()

    init {
        val db  = AppDatabase.getInstance(application)
        val prefs =  application.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        repo = Repository(db.entryDao(), prefs)
    }

    var counters : List<String>
        get() = repo.getCounterList()
        set(value) = repo.setCounterList(value)

    fun addCounter(value : String) {
        repo.setCounterList(repo.getCounterList().toMutableList() + value)
        for ((_, observer) in addCounterObservers) {
            observer.onChanged(value)
        }
    }

    fun observeAddCounter(owner : LifecycleOwner, observer: Observer<String>) {
        addCounterObservers[owner] = observer
    }

    fun renameCounter(oldName : String, newName : String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.renameCounter(oldName, newName)
            var counter = counterMap.remove(oldName)
            if (counter != null) {
                counterMap[newName] = counter
                counter.postValue(repo.getCounter(newName))
            }
        }
    }

    fun incrementCounter(name : String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.addEntry(name)
            counterMap[name]?.postValue(repo.getCounter(name))
        }
    }

    fun decrementCounter(name : String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeEntry(name)
            counterMap[name]?.postValue(repo.getCounter(name))
        }
    }

    /*suspend*/ fun getCounter(name : String) : LiveData<Counter> {
        return counterMap.getOrElse(name) {
            val counter = MutableLiveData(repo.getCounter(name))
            counterMap[name] = counter
            return counter
        }
    }

}
