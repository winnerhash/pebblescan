package io.github.g00fy2.quickiesample;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Size;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.transform.OutputTransform;
import io.github.g00fy2.quickie.CodePosition;
import io.github.g00fy2.quickie.FrozenPreviewTransform;
import io.github.g00fy2.quickie.PreviewCodeCoordinates;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = {TransformExperimental.class})
public class FrozenPreviewTransformTest {
  @Test public void heldImageAndDotAgreeThroughAllRotationsFillCroppingAndFrontCameraMirroring() {
    Bitmap raw = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
    raw.eraseColor(Color.BLUE);
    Paint paint = new Paint();
    paint.setColor(Color.RED);
    new Canvas(raw).drawRect(0, 0, 40, 20, paint);
    for (int rotation : new int[] {0, 90, 180, 270}) {
      for (boolean mirror : new boolean[] {false, true}) {
        boolean portrait = rotation % 180 != 0;
        int width = portrait ? 40 : 80;
        int height = portrait ? 80 : 40;
        int viewWidth = portrait ? 160 : 200;
        int viewHeight = portrait ? 200 : 160;
        Matrix sourceMatrix = new Matrix();
        sourceMatrix.setScale(width / 2f, height / 2f);
        sourceMatrix.postTranslate(width / 2f, height / 2f);
        float scale = Math.max((float) viewWidth / width, (float) viewHeight / height);
        Matrix targetMatrix = new Matrix();
        targetMatrix.setScale(width * scale / 2f, height * scale / 2f);
        targetMatrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
        if (mirror) targetMatrix.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f);
        Size viewport = new Size(width, height);
        OutputTransform source = new OutputTransform(sourceMatrix, viewport);
        OutputTransform target = new OutputTransform(targetMatrix, viewport);
        float[] position;
        switch (rotation) {
          case 90: position = new float[] {30f, 20f}; break;
          case 180: position = new float[] {60f, 30f}; break;
          case 270: position = new float[] {10f, 60f}; break;
          default: position = new float[] {20f, 10f};
        }
        CodePosition dot = new PreviewCodeCoordinates(source, target, width, height, viewWidth, viewHeight)
            .map(new CodePosition(position[0] / width, position[1] / height, .1f, .1f));
        Bitmap displayed = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        new Canvas(displayed).drawBitmap(raw,
            FrozenPreviewTransform.INSTANCE.create(80, 40, rotation, source, target), new Paint());
        assertEquals("rotation=" + rotation + ", mirror=" + mirror, Color.RED,
            displayed.getPixel(Math.round(dot.getX() * viewWidth), Math.round(dot.getY() * viewHeight)));
        displayed.recycle();
      }
    }
    raw.recycle();
  }
}