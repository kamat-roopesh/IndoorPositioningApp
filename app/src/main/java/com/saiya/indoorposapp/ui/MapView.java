package com.saiya.indoorposapp.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.saiya.indoorposapp.R;

/**
 * 缩放图片控件
 */
public class MapView extends ImageView {
    /** 图片长度*/
    private int mImageWidth;
    /** 图片高度 */
    private int mImageHeight;
    /** 原始缩放级别 */
    private float mScale;
    /** 地图比例尺 */
    private float mapScale;
    /** 地点X坐标 */
    private float indicatorX = 1;
    /** 地点Y坐标 */
    private float indicatorY = 1;
    /** 用于存储当前Matrix信息 */
    private Matrix mCurrentMatrix = new Matrix();
    /** 存储指示器Bitmap */
    private Bitmap mIndicatorBitmap;
    /** 指示器绘制的范围 */
    private RectF mIndicatorRect = new RectF();
    /** 触屏动作监听器,处理与ViewPager的冲突 */
    private OnMovingListener moveListener;

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        MatrixTouchListener mListener = new MatrixTouchListener();
        setOnTouchListener(mListener);
        //将缩放类型设置为CENTER_INSIDE，表示把图片居中显示,并且宽高最大值为控件宽高
        setScaleType(ScaleType.MATRIX);
        mIndicatorBitmap = BitmapFactory.decodeResource(getResources(),
                R.drawable.positioning_icon_indicator);
    }

    public MapView(Context context) {
        this(context, null);
    }

    public void setOnMovingListener(OnMovingListener listener) {
        moveListener = listener;
    }

    /**
     * 设置MapView显示的地图
     * @param bm 要显示的Bitmap对象
     * @param mapScale 设置地图的比例尺,用于准确显示位置
     */
    public void setMap(Bitmap bm, float mapScale) {
        super.setImageBitmap(bm);
        mImageWidth = bm.getWidth();
        mImageHeight = bm.getHeight();
        this.mapScale = mapScale;
/*        if (getWidth() == 0) {
            ViewTreeObserver vto = getViewTreeObserver();
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    initData();
                    //赋值结束后，移除该监听函数
                    MapView.this.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        } else {
            initData();
        }*/
        initData();
    }

    private void initData() {
        int vwidth = getWidth();
        int vheight = getHeight();
        /* 模板Matrix,用以初始化 */
        Matrix mMatrix = new Matrix();
        mScale = (float) vheight / (float) mImageHeight;
        float dx = (vwidth - mImageWidth * mScale) / 2;
        float dy = 0;
        mMatrix.setScale(mScale, mScale);
        mMatrix.postTranslate(dx, dy);
        setImageMatrix(mMatrix);
    }

    /**
     * 更新地点指示器坐标,并刷新View显示
     * @param x 新地点X坐标
     * @param y 新地点Y坐标
     */
    public void setIndicator(float x, float y, boolean isUIThread) {
        indicatorX = x;
        indicatorY = y;
        if (isUIThread) {
            invalidate();
        } else {
            postInvalidate();
        }
    }

    private float[] mTempValues = new float[9];
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mCurrentMatrix.set(getImageMatrix());
        mCurrentMatrix.getValues(mTempValues);
        float currentScale = mTempValues[Matrix.MSCALE_X];
        float scaledIndicatorLeft =
                indicatorX * mapScale * currentScale + mTempValues[Matrix.MTRANS_X] - 32;
        float scaledIndicatorTop =
                indicatorY * mapScale * currentScale + mTempValues[Matrix.MTRANS_Y] - 32;
        mIndicatorRect.set(scaledIndicatorLeft, scaledIndicatorTop,
                scaledIndicatorLeft + 64, scaledIndicatorTop + 64);
        canvas.drawBitmap(mIndicatorBitmap, null, mIndicatorRect, null);
    }

    public class MatrixTouchListener implements OnTouchListener {
        /**
         * 拖拉照片模式
         */
        private static final int MODE_DRAG = 1;
        /**
         * 放大缩小照片模式
         */
        private static final int MODE_ZOOM = 2;
        /**
         * 最大缩放级别
         */
        private float mMaxScale = 6;
        /**
         * 最小缩放级别
         */
        private float mMinScale = 0.7f;
        private int mMode = 0;
        /**
         * 缩放开始时的手指间距
         */
        private float mStartDis;
        /**
         * 当前Matrix
         */
        private Matrix mCurrentMatrix = new Matrix();

        /** 用于记录开始时候的坐标位置 */

        /**
         * 和ViewPager交互相关，判断当前是否可以左移、右移
         */
        boolean mLeftDragable;
        boolean mRightDragable;
        /**
         * 是否第一次移动
         */
        boolean mFirstMove = false;
        private PointF mStartPoint = new PointF();

        private float[] tempValues = new float[9];

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    //设置拖动模式
                    mMode = MODE_DRAG;
                    mStartPoint.set(event.getX(), event.getY());
                    startDrag();
                    checkDragable();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopDrag();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mMode == MODE_ZOOM) {
                        setZoomMatrix(event);
                    } else if (mMode == MODE_DRAG) {
                        setDragMatrix(event);
                    } else {
                        stopDrag();
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    mMode = MODE_ZOOM;
                    mStartDis = distance(event);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    break;
                default:
                    break;
            }
            return true;
        }

        /**
         * 子控件开始进入移动状态，令ViewPager无法拦截对子控件的Touch事件
         */
        private void startDrag() {
            if (moveListener != null) {
                moveListener.startDrag();
            }

        }

        /**
         * 子控件开始停止移动状态，ViewPager将拦截对子控件的Touch事件
         */
        private void stopDrag() {
            if (moveListener != null) {
                moveListener.stopDrag();
            }
        }

        /**
         * 根据当前图片左右边缘设置可拖拽状态
         */
        private void checkDragable() {
            mLeftDragable = true;
            mRightDragable = true;
            mFirstMove = true;
            getImageMatrix().getValues(tempValues);
/*            //图片左边缘离开左边界，表示不可右移
            if (tempValues[Matrix.MTRANS_X] >= 0) {
                mRightDragable = false;
            }*/
            //图片右边缘离开右边界，表示不可左移
            if (tempValues[Matrix.MTRANS_X] <= getWidth() - mImageWidth * tempValues[Matrix.MSCALE_X]) {
                mLeftDragable = false;
            }
        }

        /**
         * 设置拖拽状态下的Matrix
         *
         * @param event 封装的动作对象
         */
        public void setDragMatrix(MotionEvent event) {
            float dx = event.getX() - mStartPoint.x; // 得到x轴的移动距离
            float dy = event.getY() - mStartPoint.y; // 得到x轴的移动距离
            //避免和双击冲突,大于10f才算是拖动
            if (Math.sqrt(dx * dx + dy * dy) > 10f) {
                mStartPoint.set(event.getX(), event.getY());
                //在当前基础上移动
                mCurrentMatrix.set(getImageMatrix());
                mCurrentMatrix.getValues(tempValues);
                dy = checkDyBound(tempValues, dy);
                dx = checkDxBound(tempValues, dx, dy);
                mCurrentMatrix.postTranslate(dx, dy);
                setImageMatrix(mCurrentMatrix);
            }
        }

        /**
         * 和当前矩阵对比，检验dy，使图像移动后不会超出ImageView边界
         *
         * @param values 当前矩阵的值
         * @param dy     两次动作y方向的差值
         * @return 优化后的dy
         */
        private float checkDyBound(float[] values, float dy) {
            float height = getHeight();
            if (values[Matrix.MSCALE_X] >= mScale) {
                if (values[Matrix.MTRANS_Y] + dy > 0) {
                    dy = -values[Matrix.MTRANS_Y];
                } else if (values[Matrix.MTRANS_Y] + dy < height - mImageHeight * values[Matrix.MSCALE_Y]) {
                    dy = height - mImageHeight * values[Matrix.MSCALE_Y] - values[Matrix.MTRANS_Y];
                }
            } else {
                if (values[Matrix.MTRANS_Y] + dy < 0) {
                    dy = -values[Matrix.MTRANS_Y];
                } else if (values[Matrix.MTRANS_Y] + dy > height - mImageHeight * values[Matrix.MSCALE_Y]) {
                    dy = height - mImageHeight * values[Matrix.MSCALE_Y] - values[Matrix.MTRANS_Y];
                }
            }
            return dy;
        }

        /**
         * 和当前矩阵对比，检验dx，使图像移动后不会超出ImageView边界
         *
         * @param values 当前矩阵的值
         * @param dx     两次动作x方向的差值
         * @return 优化后的dx
         */
        private float checkDxBound(float[] values, float dx, float dy) {
            float width = getWidth();
            if (!mLeftDragable && dx < 0) {
                //加入和y轴的对比，表示在监听到垂直方向的手势时不切换Item
                if (Math.abs(dx) * 0.4f > Math.abs(dy) && mFirstMove) {
                    stopDrag();
                }
                return 0;
            }
/*            if (!mRightDragable && dx > 0) {
                //加入和y轴的对比，表示在监听到垂直方向的手势时不切换Item
                if (Math.abs(dx) * 0.4f > Math.abs(dy) && mFirstMove) {
                    stopDrag();
                }
                return 0;
            }*/
            mLeftDragable = true;
            mRightDragable = true;
            mFirstMove = false;
            if (mImageWidth * values[Matrix.MSCALE_X] < width) {
                return -values[Matrix.MTRANS_X];
            }
            if (values[Matrix.MTRANS_X] + dx > 0) {
                dx = -values[Matrix.MTRANS_X];
            } else if (values[Matrix.MTRANS_X] + dx <
                    width - mImageWidth * values[Matrix.MSCALE_X]) {
                dx = -(mImageWidth * values[Matrix.MSCALE_X] - width) - values[Matrix.MTRANS_X];
            }
            return dx;
        }

        /**
         * 设置缩放Matrix
         *
         * @param event 封装的动作event
         */
        private void setZoomMatrix(MotionEvent event) {
            //只有同时触屏两个点的时候才执行
            if (event.getPointerCount() < 2) {
                return;
            }
            float endDis = distance(event);// 结束距离
            if (endDis > 10f) { // 两个手指并拢在一起的时候像素大于10
                float scale = endDis / mStartDis;// 得到缩放倍数
                mStartDis = endDis;//重置距离
                mCurrentMatrix.set(getImageMatrix());//初始化Matrix
                mCurrentMatrix.getValues(tempValues);
                scale = checkScale(scale, tempValues);
                mCurrentMatrix.postScale(scale, scale, getWidth() / 2, getHeight() / 2);
                mCurrentMatrix.getValues(tempValues);
                setImageMatrix(mCurrentMatrix);
            }
        }

        /**
         * 检验scale，使图像缩放后不会超出最大倍数
         *
         * @param scale  要缩放的倍数
         * @param values 当前矩阵的值
         * @return 优化后的scale
         */
        private float checkScale(float scale, float[] values) {
            if (scale * values[Matrix.MSCALE_X] > mMaxScale) {
                scale = mMaxScale / values[Matrix.MSCALE_X];
            }
            if (scale * values[Matrix.MSCALE_X] < mMinScale) {
                scale = mMinScale / values[Matrix.MSCALE_X];
            }
            return scale;
        }

        /**
         * 计算两个手指间的距离
         *
         * @param event 封装的动作event
         * @return 两个手指间的距离
         */
        private float distance(MotionEvent event) {
            float dx = event.getX(1) - event.getX(0);
            float dy = event.getY(1) - event.getY(0);
            /** 使用勾股定理返回两点之间的距离 */
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

    }

    /**
     * MatrixImageView移动监听接口,用以组织ViewPager对Move操作的拦截
     */
    public interface OnMovingListener {
        void startDrag();
        void stopDrag();
    }

}
