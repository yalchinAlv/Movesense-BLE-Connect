package com.movesense.samples.sensorsample;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.movesense.samples.sensorsample.model.ChartViewModel;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ChartActivity extends Fragment {

    public static ChartActivity newInstance() {
        ChartActivity fragment = new ChartActivity();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.activity_chart, container, false);

        LineChart chart = view.findViewById(R.id.chart);
        chart.setBorderWidth(4);
        List<Entry> bpmEntries = new LinkedList<>();
        List<Entry> accEntries = new LinkedList<>();

        if (getActivity() != null) {
            ChartViewModel chartViewModel = ViewModelProviders.of(getActivity()).get(ChartViewModel.class);
            chartViewModel.getLiveData().observe(getActivity(), new Observer<Entry>() {
                @Override
                public void onChanged(@Nullable Entry entry) {
                    if (bpmEntries.isEmpty()) {
                        accEntries.add(new Entry(0, entry.getX()));
                        bpmEntries.add(new Entry(0, entry.getY()));
                    }
                    else {
                        int lastX = (int)((LinkedList<Entry>) accEntries).getLast().getX() + 10;
                        accEntries.add(new Entry(lastX, entry.getX()));
                        bpmEntries.add(new Entry(lastX, entry.getY()));
                    }

                    if (bpmEntries.size() > 20) {
                        ((LinkedList<Entry>) accEntries).removeFirst();
                        ((LinkedList<Entry>) bpmEntries).removeFirst();
                    }

                    ArrayList<ILineDataSet> lines = new ArrayList<>();
                    LineDataSet accDataSet = new LineDataSet(accEntries, "Acceleration");
                    accDataSet.setCircleColor(Color.BLUE);
                    accDataSet.setColor(Color.BLUE);
                    LineDataSet bpmDataSet = new LineDataSet(bpmEntries, "BPM");
                    bpmDataSet.setCircleColor(Color.RED);
                    bpmDataSet.setColor(Color.RED);

//                    LineData accLineData = new LineData(accDataSet);
//                    LineData bpmLineData = new LineData(bpmDataSet);

                    lines.add(accDataSet);
                    lines.add(bpmDataSet);

                    chart.setData(new LineData(lines));
//                    chart.setData(bpmLineData);
                    chart.invalidate(); // refresh
                }
            });
        }
        return view;
    }
}