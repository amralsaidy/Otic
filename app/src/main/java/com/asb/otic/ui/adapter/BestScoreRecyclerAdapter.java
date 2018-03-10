package com.asb.otic.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.asb.otic.R;
import com.asb.otic.data.DatabaseHandler;
import com.asb.otic.model.Score;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class BestScoreRecyclerAdapter extends RecyclerView.Adapter {
    Context context;
    private ArrayList<Score> scores;

    public BestScoreRecyclerAdapter(Context context, int size) {
        this.context = context;
        DatabaseHandler databaseHandler = new DatabaseHandler(context);
        scores = databaseHandler.getWins(size);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_best_score_list, parent, false);
        return new BestScoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        BestScoreViewHolder bestScoreViewHolder = (BestScoreViewHolder) holder;
        Score score = scores.get(position);
        bestScoreViewHolder.playerNameTextView.setText(score.getName());
        bestScoreViewHolder.noWinsTextView.setText(String.valueOf(score.getNoWins()));
    }

    @Override
    public int getItemCount() {
        return scores.size();
    }

    class BestScoreViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.player_name_tv_id)
        TextView playerNameTextView;
        @BindView(R.id.no_wins_tv_id)
        TextView noWinsTextView;

        public BestScoreViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
