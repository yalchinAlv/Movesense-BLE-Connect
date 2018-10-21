package com.movesense.samples.sensorsample.model;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;

import com.github.mikephil.charting.data.Entry;

public class ChartViewModel extends ViewModel {
    private MutableLiveData<Entry> liveData;

    public MutableLiveData<Entry> getLiveData() {
        if (liveData == null) {
            liveData = new MutableLiveData<>();
        }
        return liveData;
    }
}
