package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

/**
 * Adapter untuk menampilkan daftar hasil deteksi
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ResultViewHolder> {
    
    private List<ResultItem> resultList;
    private Context context;
    private OnResultClickListener clickListener;
    private OnDeleteClickListener deleteListener;
    
    public interface OnResultClickListener {
        void onResultClick(ResultItem item, int position);
    }
    
    public interface OnDeleteClickListener {
        void onDeleteClick(ResultItem item, int position);
    }
    
    public ResultsAdapter(Context context, List<ResultItem> resultList) {
        this.context = context;
        this.resultList = resultList;
    }
    
    public void setOnResultClickListener(OnResultClickListener listener) {
        this.clickListener = listener;
    }
    
    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }
    
    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_result, parent, false);
        return new ResultViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        ResultItem item = resultList.get(position);
        
        // Load thumbnail
        if (item.getImageFile() != null && item.getImageFile().exists()) {
            try {
                // Load scaled bitmap to avoid memory issues
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4; // Scale down by 4x
                Bitmap bitmap = BitmapFactory.decodeFile(item.getImageFile().getAbsolutePath(), options);
                holder.imgThumbnail.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        
        // Set text data
        holder.txtWeight.setText("Bobot: " + item.getWeight() + " kg");
        holder.txtDistance.setText("Jarak: " + item.getDistance() + " cm");
        holder.txtBbox.setText("BBox: " + item.getBboxSize());
        holder.txtFileName.setText(item.getFileName());
        
        // Click listener for item
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onResultClick(item, holder.getAdapterPosition());
            }
        });
        
        // Click listener for delete
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(item, holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return resultList != null ? resultList.size() : 0;
    }
    
    public void updateData(List<ResultItem> newList) {
        this.resultList = newList;
        notifyDataSetChanged();
    }
    
    public void removeItem(int position) {
        if (position >= 0 && position < resultList.size()) {
            resultList.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    static class ResultViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView txtWeight;
        TextView txtDistance;
        TextView txtBbox;
        TextView txtFileName;
        ImageButton btnDelete;
        
        public ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            txtWeight = itemView.findViewById(R.id.txtWeight);
            txtDistance = itemView.findViewById(R.id.txtDistance);
            txtBbox = itemView.findViewById(R.id.txtBbox);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
