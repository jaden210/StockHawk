package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import com.db.chart.Tools;
import com.db.chart.model.LineSet;
import com.db.chart.view.AxisController;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MyStocksDetailActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = MyStocksDetailActivity.class.getSimpleName();
    private String quoteID;
    private String quoteSymbol;
    private static final int CURSOR_LOADER_ID = 1;
    private TextView tvPrice, tvChangePercent;
    private ArrayList<String> labels;
    private ArrayList<Float> values;
    private int rangeMin, rangeMax;

    private String[] mLabels;
    private float[] mValues;

    LineChartView lineChartView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_stocks_detail);

        Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeActionContentDescription(R.string.nav_home);

        quoteID = getIntent().getStringExtra("id");
        Log.d(TAG, "ID: " + quoteID);

        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

        tvPrice = (TextView) findViewById(R.id.tvPrice);
        String tvPriceDescription = "The Stock price for " + quoteSymbol + " is " + tvPrice;
        tvPrice.setContentDescription(tvPriceDescription);
        tvChangePercent = (TextView) findViewById(R.id.tvChangePercent);
        lineChartView = (LineChartView) findViewById(R.id.linechart);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This narrows the return to only the stocks that are most current.
        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{QuoteColumns.SYMBOL,
                        QuoteColumns.BIDPRICE,
                        QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE,
                        QuoteColumns.ISUP,
                        QuoteColumns.CREATED, QuoteColumns.ISCURRENT},
                QuoteColumns._ID + " = ?",
                new String[]{quoteID},
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        //Log.d(TAG, DatabaseUtils.dumpCursorToString(data));
        data.moveToFirst();
        quoteSymbol = data.getString(data.getColumnIndex(QuoteColumns.SYMBOL));
        tvPrice.setText(data.getString(data.getColumnIndex(QuoteColumns.BIDPRICE)));
        tvChangePercent.setText(data.getString(data.getColumnIndex(QuoteColumns.PERCENT_CHANGE)));

        if (data.getInt(data.getColumnIndex("is_up")) == 1) {
            tvChangePercent.setTextColor(ContextCompat.getColor(this, R.color.high_green));
        } else {
            tvChangePercent.setTextColor(ContextCompat.getColor(this, R.color.low_red));
        }

        updateChart();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }


    private void updateChart() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://chartapi.finance.yahoo.com/instrument/1.0/" + quoteSymbol + "/chartdata;type=close;range=1d/json")
                .build();

        client.newCall(request).enqueue(new Callback() {

                                            @Override
                                            public void onResponse(Response response) throws IOException {
                                                if (response.code() == 200) {
                                                    try {
                                                        // Trim response string
                                                        String result = response.body().string();
                                                        if (result.startsWith("finance_charts_json_callback( ")) {
                                                            result = result.substring(29, result.length() - 2);
                                                        }

                                                        // Parse JSON
                                                        JSONObject object = new JSONObject(result);

                                                        //Range MIN/MAX value
                                                        JSONObject range = object.getJSONObject("ranges");
                                                        JSONObject closeItem = range.getJSONObject("close");
                                                        rangeMin = closeItem.getInt("min");
                                                        rangeMax = closeItem.getInt("max");

                                                        //Series values
                                                        labels = new ArrayList<>();
                                                        values = new ArrayList<>();
                                                        JSONArray series = object.getJSONArray("series");
                                                        for (int i = 0; i < series.length(); i++) {
                                                            JSONObject seriesItem = series.getJSONObject(i);

                                                            int unixTime = seriesItem.getInt("Timestamp");
                                                            long timestamp = unixTime * 1000;  // msec

                                                            Date d = new Date(timestamp);
                                                            SimpleDateFormat localDateFormat = new SimpleDateFormat("HH:mm");
                                                            String time = localDateFormat.format(d);
                                                            labels.add(time);
                                                            values.add(Float.parseFloat(seriesItem.getString("close")));
                                                        }
                                                        onDownloadCompleted();

                                                    } catch (Exception e) {
                                                        Log.d(TAG, "onFailure: Failed");
                                                        e.printStackTrace();
                                                    }
                                                } else {
                                                    Log.d(TAG, "onFailure: Failed");
                                                }
                                            }

                                            private void onDownloadCompleted() {
                                                MyStocksDetailActivity.this.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mLabels = new String[labels.size()];
                                                        mValues = new float[values.size()];
                                                        labels.toArray(mLabels);
                                                        int i = 0;
                                                        for (Float f : values) {
                                                            mValues[i++] = (f != null ? f : Float.NaN);
                                                        }

                                                        LineSet dataset = new LineSet(mLabels, mValues);
                                                        dataset.setColor(Color.parseColor("#53c1bd")).setThickness(3);
                                                        lineChartView.addData(dataset);

                                                        Paint gridPaint = new Paint();
                                                        gridPaint.setColor(Color.parseColor("#000000"));
                                                        gridPaint.setStyle(Paint.Style.STROKE);
                                                        gridPaint.setAntiAlias(true);
                                                        gridPaint.setStrokeWidth(Tools.fromDpToPx(0));

                                                        lineChartView.setBorderSpacing(1)
                                                                .setXLabels(AxisController.LabelPosition.NONE)
                                                                .setYLabels(AxisController.LabelPosition.OUTSIDE)
                                                                .setXAxis(false)
                                                                .setYAxis(false)
                                                                .setGrid(ChartView.GridType.FULL, gridPaint)
                                                                .setAxisBorderValues(rangeMin - 1, rangeMax + 1)
                                                                .setBorderSpacing(Tools.fromDpToPx(1));

                                                        lineChartView.show();
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onFailure (Request request, IOException e){
                                                Log.d(TAG, "onFailure: Failed");
                                            }
                                        }

        );
    }

}
