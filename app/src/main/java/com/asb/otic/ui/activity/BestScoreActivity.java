package com.asb.otic.ui.activity;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.asb.otic.R;
import com.asb.otic.data.DatabaseHandler;
import com.asb.otic.ui.adapter.BestScoreRecyclerAdapter;
import com.asb.otic.ui.custom.CDialog;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class BestScoreActivity extends BaseActivity {
    @BindView(R.id.status_bar_id)
    View statusBarView;
    @BindView(R.id.board_size_2_tv_id)
    TextView boardSize2TextView;
    @BindView(R.id.board_size_3_tv_id)
    TextView boardSize3TextView;
    @BindView(R.id.board_size_4_tv_id)
    TextView boardSize4TextView;
    @BindView(R.id.board_size_5_tv_id)
    TextView boardSize5TextView;
    @BindView(R.id.size_two_rv_id)
    RecyclerView sizeTwoRecyclerView;
    @BindView(R.id.size_three_rv_id)
    RecyclerView sizeThreeRecyclerView;
    @BindView(R.id.size_four_rv_id)
    RecyclerView sizeFourRecyclerView;
    @BindView(R.id.size_five_rv_id)
    RecyclerView sizeFiveRecyclerView;
    @BindView(R.id.clear_data_iv_id)
    public ImageView clearDataImageView;

    private CDialog cDialog;
    private DatabaseHandler databaseHandler;
    private BestScoreRecyclerAdapter bestScoreTwoRecyclerAdapter, bestScoreThreeRecyclerAdapter,
            bestScoreFourRecyclerAdapter, bestScoreFiveRecyclerAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_best_score);
        ButterKnife.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow(); // in Activity's onCreate() for instance
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        statusBarView.setBackgroundColor(COLOR1);

        boardSize2TextView.setBackgroundColor(BaseActivity.COLOR1);
        boardSize3TextView.setBackgroundColor(BaseActivity.COLOR1);
        boardSize4TextView.setBackgroundColor(BaseActivity.COLOR1);
        boardSize5TextView.setBackgroundColor(BaseActivity.COLOR1);

        sizeTwoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sizeThreeRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sizeFourRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sizeFiveRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        bestScoreTwoRecyclerAdapter = new BestScoreRecyclerAdapter(this, 2);
        sizeTwoRecyclerView.setAdapter(bestScoreTwoRecyclerAdapter);

        bestScoreThreeRecyclerAdapter = new BestScoreRecyclerAdapter(this, 3);
        sizeThreeRecyclerView.setAdapter(bestScoreThreeRecyclerAdapter);

        bestScoreFourRecyclerAdapter = new BestScoreRecyclerAdapter(this, 4);
        sizeFourRecyclerView.setAdapter(bestScoreFourRecyclerAdapter);

        bestScoreFiveRecyclerAdapter = new BestScoreRecyclerAdapter(this, 5);
        sizeFiveRecyclerView.setAdapter(bestScoreFiveRecyclerAdapter);


        databaseHandler = new DatabaseHandler(this);

        clearDataImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cDialog = new CDialog.Builder(BestScoreActivity.this)
                        .setTitle(" ")
                        .setMessage(getString(R.string.clear_all_data))
                        .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                databaseHandler.deleteAll();
                                recreate();
                            }
                        })
                        .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        })
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    @OnClick(R.id.back_iv_id)
    public void back() {
        super.onBackPressed();
    }

}
