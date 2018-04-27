package com.example.applockertest.cameratest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;


/**
 * 自定义相机
 * 通过设置xml文件中的组件大小，可以实现入侵拍摄
 */
public class CameraSurface extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "CameraSurface:";
    private static final String ANTI_PEEP = "/CameraTest";
    private boolean IsPreView;
    private boolean IsCapture;

    public Camera mCamera;
    protected SurfaceHolder mHolder;

    //用于筛选最佳像素
    private List<Camera.Size> mSupportedPreviewSizes;
    private Camera.Size mPictureSize;

    /**
     * 延迟拍摄时间
     */
    private static final int DELAY_CAPTURE_TIME = 300;


    public CameraSurface(Context paramContext, AttributeSet paramAttributeSet) {
        super(paramContext, paramAttributeSet);

        mHolder = getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(this);

        IsPreView = false;
        IsCapture = false;
    }

    private void setPreferredFormat(Camera.Parameters paramParameters,
                                    int paramInt) {
        Iterator<Integer> localIterator = paramParameters
                .getSupportedPreviewFormats().iterator();
        while (true) {
            if (!localIterator.hasNext())
                return;
            if ((localIterator.next()) == paramInt) {
                paramParameters.setPreviewFormat(paramInt);
            }
        }
    }

    private void setPreferredSize(Camera.Parameters paramParameters,
                                  int paramInt1, int paramInt2) {
        Iterator localIterator = paramParameters.getSupportedPreviewSizes()
                .iterator();
        while (true) {
            if (!localIterator.hasNext())
                return;
            Camera.Size localSize = (Camera.Size) localIterator.next();
            if ((localSize.width == paramInt1)
                    && (localSize.height == paramInt2))
                paramParameters.setPreviewSize(paramInt1, paramInt2);
        }
    }

    public void surfaceCreated(SurfaceHolder paramSurfaceHolder) {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(getFrontCameraId());
                updateCameraDisplayOrientation();
                mCamera.setPreviewDisplay(paramSurfaceHolder);
            } catch (IOException localIOException) {
                mCamera.release();
                mCamera = null;
            }
        }
    }

    public void surfaceChanged(SurfaceHolder paramSurfaceHolder, int paramInt1,
                               int paramInt2, int paramInt3) {
        if (mCamera == null)
            return;
        if (IsPreView)
            mCamera.stopPreview();
        Camera.Parameters localParameters = mCamera.getParameters();
        setPreferredSize(localParameters, paramInt2, paramInt3);
        setPreferredFormat(localParameters, paramInt1);
        mCamera.setParameters(localParameters);
        setPictureSize();
    }


    public void surfaceDestroyed(SurfaceHolder paramSurfaceHolder) {
        if (mCamera != null) {
            if (IsPreView) {
                mCamera.stopPreview();
            }
            IsPreView = false;
            mCamera.release();
            mCamera = null;
        }
    }


    public void openCamera() {
        mCamera.startPreview();
        IsPreView = true;
    }

    public void takePicture() {
        Handler mHandler = new Handler();
        try {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (!IsCapture){
                        camera.takePicture(null, null, mJpegCallback);
                        IsCapture = true;
                    }
            }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        //当对焦失败时调用照相功能，即无论对焦成功与否都要拍照
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!IsCapture) {
                    mCamera.takePicture(null, null, mJpegCallback);
                }
            }
        }, DELAY_CAPTURE_TIME);
    }


    public void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 获取前置摄像头的Id
     *
     * @return 没有前置时返回-1
     */
    private static int getFrontCameraId() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 前置摄像头设置为镜面反向
     */
    private void updateCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(1, info);
        int result = (360 - info.orientation) % 360;
        mCamera.setDisplayOrientation(result);
    }


    /**
     * 返回照片的JPEG格式的数据
     */
    private Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            if (camera.getParameters().getPictureFormat() == PixelFormat.JPEG) {

                //存储拍照获得的图片
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        saveJPG(data);
                    }
                }).start();
            }
        }
    };


    /**
     * 保存JPG图片
     *
     * @return 保存路径
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void saveJPG(byte[] data) {
        FileOutputStream fileOutputStream = null;

        try {
            //判断是否装有SD卡
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

                File mPhotoFile = createPeepFile();
                if (!mPhotoFile.exists()) {
                    File parentFile = mPhotoFile.getParentFile();

                    if (!parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    //创建文件
                    mPhotoFile.createNewFile();
                }
                fileOutputStream = new FileOutputStream(mPhotoFile);

                Bitmap bitmap = handleBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fileOutputStream);
                bitmap.recycle();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (fileOutputStream != null) {
                try {
                    // 关闭流，否则会引起文件无法写入的问题：java.io.IOException: open failed: EBUSY (Device or resource busy)
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            IsCapture = false;
        }
    }

    private synchronized static File createPeepFile() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        return new File(path + File.separator + ANTI_PEEP + File.separator + System.currentTimeMillis() + ".jpg");
    }

    /**
     * 处理Bitmap效果:旋转
     *
     * @param src 原始Bitmap
     * @return 处理后的Bitmap
     */
    private Bitmap handleBitmap(Bitmap src) {
        // 旋转照片
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(1, info);
        Matrix matrix = new Matrix();
        matrix.setRotate(info.orientation);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    private void setPictureSize() {
        int mScreenWidth;
        int mScreenHeight;
        WindowManager wm = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            mScreenWidth = wm.getDefaultDisplay().getWidth();
            mScreenHeight = wm.getDefaultDisplay().getHeight();
        } else {
            mScreenWidth = 50;
            mScreenHeight = 50;
        }
        mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();

        if (mSupportedPreviewSizes != null) {
            mPictureSize = getOptimalPreviewSize(mSupportedPreviewSizes,
                    mScreenWidth, mScreenHeight);
        }
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPictureSize(mPictureSize.width, mPictureSize.height);
        mCamera.setParameters(parameters);
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double aspectTolerance = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > aspectTolerance) {
                continue;
            }
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

}
