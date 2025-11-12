package com.qmdeve.liquidglass.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.qmdeve.liquidglass.demo.util.Utils;
import com.qmdeve.liquidglass.widget.LiquidGlassView;

import java.io.IOException;
import java.io.InputStream;

public class ElasticLiquidGlassViewActivity  extends AppCompatActivity {

    private ImageView images;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        images.setImageBitmap(bitmap);
                    }

                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException ignored) {
                }
            }
        });

        setContentView(R.layout.activity_elastic_liquid_glass_view);
        Utils.transparentStatusBar(getWindow());
        Utils.transparentNavigationBar(getWindow());

        Button button = findViewById(R.id.button);
        images = findViewById(R.id.images);

        ViewGroup.MarginLayoutParams buttonParams = (ViewGroup.MarginLayoutParams) button.getLayoutParams();
        buttonParams.topMargin = (int) (Utils.getStatusBarHeight(this) + Utils.dp2px(getResources(), 6));
        button.setLayoutParams(buttonParams);

        button.setOnClickListener(v -> {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } else {
                Toast.makeText(this, getString(R.string.a1), Toast.LENGTH_SHORT).show();
            }
        });

        LiquidGlassView liquidGlassView = findViewById(R.id.liquidGlassView);
        ViewGroup content = findViewById(R.id.content_container);

        liquidGlassView.bind(content);
        liquidGlassView.setDraggableEnabled(true);
        liquidGlassView.setElasticEnabled(true);
    }
}
