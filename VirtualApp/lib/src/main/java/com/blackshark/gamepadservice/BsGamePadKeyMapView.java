package com.blackshark.gamepadservice;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadKeyMapView extends ViewGroup {
    private static final boolean DEBUG = false;
    private static final boolean HANDLE_D1_ONLY = true;
    private static final String TAG = "BsGamePadKeyMapView";
    private static final String buttonNameList[] =
        {"start", "select", "A", "B", "X", "Y", "L1", "R1", "L2", "R2", "D1", "D2", "hat"};
    private static final int NAV_SIZE_PT = 144;
    private int mScreenWidth;
    private int mScreenHeight;
    private Context mContext;
    private boolean mShow = false;
    private WindowManager mWindowManager;
    private BsGamePadMapper mMapper;
    private BsGamePadService mService;
    private BsKeyMapperDialog mAddMapDialog;
    private BsKeyMapperDialog mDelMapDialog;
    private static final int LONG_PRESS_FOR_ADD	= 1;
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int DOUBLE_TAP_FOR_DEL	= 2;
    private static final int DOUBLE_TAP_TIMEOUT	= ViewConfiguration.getDoubleTapTimeout();
    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case LONG_PRESS_FOR_ADD:
                    handleLongPressForAdd((MotionEvent)msg.obj);
                    break;
                case DOUBLE_TAP_FOR_DEL:
                    break;
                default:
                    throw new RuntimeException("Unkown message " + msg);
            }
        }
    };

    BsGamePadKeyMapView(Context context, BsGamePadMapper mapper, BsGamePadService service){
        super(context);
        mContext = context;
        mMapper = mapper;
        mService = service;
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        setBackgroundColor(0x80CCCCCC);

        for (int i=0;i<mapper.getGamePadMapper().size();i++){
            StringBuilder stringBuilder = new StringBuilder();
            int index = 0;
            int button = mapper.getGamePadMapper().keyAt(i);
            for (;index<BsGamePadController.GAMEPAD_CONTROL_COUNT;index++){
                if ((button&(0x1<<index)) != 0){
                    stringBuilder.append(buttonNameList[index]);
                    stringBuilder.append("+");
                }
            }
            stringBuilder.deleteCharAt(stringBuilder.length()-1);

            CircleTextView view = new CircleTextView(context);
            view.setText(stringBuilder.toString());
            view.setTextSize(16);
            view.setTag(button);
            view.setGravity(Gravity.CENTER);
            view.setBackgroundColor(Color.RED);
            view.setLeft((int)mMapper.getGamePadMapper().get(button).getCenterX());
            view.setTop((int)mMapper.getGamePadMapper().get(button).getCenterY());
            view.setRight((int)mMapper.getGamePadMapper().get(button).getCenterX()+(int)getTextWidth(view));
            view.setBottom((int)mMapper.getGamePadMapper().get(button).getCenterY()+(int)(getTextHeight(view)*2));
            addView(view);
        }

        mAddMapDialog = new BsKeyMapperDialog(mContext);
        mAddMapDialog.enableInputDetect(true);
        mAddMapDialog.setTitle("Add KeyMap");
        mAddMapDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAddMapDialog.setPositiveButton("OK",
            new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){
                    InputView inputView = (InputView)mAddMapDialog.getView();
                    if (inputView == null || inputView.getTag() == null){
                        return;
                    }

                    MotionEvent event = mAddMapDialog.getInputMotionEvent();
                    CircleTextView view = new CircleTextView(mContext);
                    view.setText(inputView.getText());
                    view.setTag(inputView.getTag());
                    view.setGravity(Gravity.CENTER);
                    view.setBackgroundColor(Color.RED);
                    if (DEBUG){
                        Log.d(TAG, "onClick  event = " + event);
                    }
                    view.setLeft((int)event.getX());
                    view.setTop((int)event.getY());
                    view.setRight((int)event.getX()+(int)getTextWidth(view));
                    view.setBottom((int)event.getY()+(int)(getTextHeight(view)*2));
                    int taskMask = (int)view.getTag();
                    int r = 0;
                    if (taskMask > BsGamePadController.GAMEBUTTON_COUNT){
                        r = 300;
                    }
                    mMapper.getGamePadMapper().put(taskMask,
                        new BsGamePadMapper.ButtonPadMap(event.getRawX(), event.getRawY(), r));
                    addView(view);
                    event.recycle();
                }
            });
        mAddMapDialog.setNegativeButton("CANCEL", null);

        mDelMapDialog = new BsKeyMapperDialog(mContext);
        mDelMapDialog.setTitle("Del KeyMap");
        mDelMapDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDelMapDialog.setPositiveButton("OK",
            new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){
                    View view = mDelMapDialog.getDelView();
                    mMapper.getGamePadMapper().remove(view.getTag());
                    removeView(view);
                }
            });
        mDelMapDialog.setNegativeButton("CANCEL", null);
    }

    public void dismissChildView(){
        if (mAddMapDialog.isShowing()){
            mAddMapDialog.dismiss();
        }

        if (mDelMapDialog.isShowing()){
            mDelMapDialog.dismiss();
        }
    }

    public void setShow(boolean show){
        mShow = show;
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            mScreenHeight += NAV_SIZE_PT;
        } else {
            mScreenWidth += NAV_SIZE_PT;
        }
    }

    public boolean getShow(){
        return mShow;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b){}

    private float getTextWidth(TextView view){
        return view.getPaint().measureText((String)view.getText());
    }

    private float getTextHeight(TextView view){
        Rect rect = new Rect();
        view.getPaint().getTextBounds(view.getText().toString(), 0, view.getText().toString().length(), rect);
        return rect.height();
    }

    private void handleLongPressForAdd(MotionEvent event){
        ((InputView)mAddMapDialog.getView()).setText("Input...");
        ((InputView)mAddMapDialog.getView()).setTag(null);
        if (DEBUG){
            Log.d(TAG, "handleLongPressForAdd : event = " + event);
        }
        mAddMapDialog.setInputMotionEvent(event);
        mAddMapDialog.show();
    }

    private void handleDoubleTapForDel(CircleTextView view){
        mDelMapDialog.setDelView(view);
        mDelMapDialog.show();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START){
            if (DEBUG){
                Log.d(TAG, "onKeyUp : mAddMapDialog.isShowing() = " + mAddMapDialog.isShowing());
                Log.d(TAG, "onKeyUp : mDelMapDialog.isShowing() = " + mDelMapDialog.isShowing());
            }
            if (mAddMapDialog.isShowing() || mDelMapDialog.isShowing()){
                return false;
            }

            mMapper.syncGamePadMapper();
            setShow(false);
            mWindowManager.removeView(this);
            mService.hookMethod();
        }

        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getAction() & MotionEvent.ACTION_MASK){
            case MotionEvent.ACTION_DOWN:
                mHandler.removeMessages(LONG_PRESS_FOR_ADD);
                Message msg = mHandler.obtainMessage(LONG_PRESS_FOR_ADD);
                msg.obj = event;
                mHandler.sendMessageDelayed(msg, LONG_PRESS_TIMEOUT);
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeMessages(LONG_PRESS_FOR_ADD);
                break;
        }
        return true;
    }

    private class InputView extends TextView {
        private int mTouchMask = 0;
        private int mDownEventState = 0;
        private ArrayList<String> mInputStringList = new ArrayList<String>();
        private static final int DIRECTION_PAD_UP   = 3;
        private static final int DIRECTION_PAD_UP_TIMEOUT = 70;
        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg){
                switch(msg.what){
                    case DIRECTION_PAD_UP:
                        if (DEBUG) {
                            Log.d(TAG, "mDownEventState = " + mDownEventState);
                        }
                        //mDownEventState &= ~(0x1 << msg.arg1);
                        break;
                    default:
                        throw new RuntimeException("Unkown message " + msg);
                }
            }
        };

        public InputView(Context context){
            super(context);

        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event){
            if (DEBUG){
                Log.d(TAG, "onKeyDown keyCode = " + keyCode);
                Log.d(TAG, "onKeyDown event = " + event);
            }
            if (event.getSource() != (InputDevice.SOURCE_GAMEPAD|InputDevice.SOURCE_KEYBOARD)){
                return false;
            }

            int index = 0;
            if (mDownEventState == 0){
                mTouchMask = 0;
                mInputStringList.clear();
            }

            switch(keyCode){
                case KeyEvent.KEYCODE_BUTTON_A:
                    mInputStringList.add("A");
                    index = BsGamePadController.GAMEBUTTON_BUTTON_A;
                    break;
                case KeyEvent.KEYCODE_BUTTON_B:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_B;
                    mInputStringList.add("B");
                    break;
                case KeyEvent.KEYCODE_BUTTON_X:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_X;
                    mInputStringList.add("X");
                    break;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_Y;
                    mInputStringList.add("Y");
                    break;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_L1;
                    mInputStringList.add("L1");
                    break;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_R1;
                    mInputStringList.add("R1");
                    break;
                case KeyEvent.KEYCODE_BUTTON_L2:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_L2;
                    mInputStringList.add("L2");
                    break;
                case KeyEvent.KEYCODE_BUTTON_R2:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_R2;
                    mInputStringList.add("R2");
                    break;
                default:
                    return true;
            }

            mDownEventState |= (0x1 << index);

            StringBuilder strBuilder = new StringBuilder();
            for (int i=0;i<mInputStringList.size();i++){
                strBuilder.append(mInputStringList.get(i)+"+");
            }

            strBuilder.deleteCharAt(strBuilder.length()-1);
            mTouchMask |= (0x1 << index);

            setText(strBuilder);
            setTag(mTouchMask);
            return true;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event){
            int index = -1;
            switch(keyCode){
                case KeyEvent.KEYCODE_BUTTON_A:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_A;
                    break;
                case KeyEvent.KEYCODE_BUTTON_B:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_B;
                    break;
                case KeyEvent.KEYCODE_BUTTON_X:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_X;
                    break;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_Y;
                    break;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_L1;
                    break;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_R1;
                    break;
                case KeyEvent.KEYCODE_BUTTON_L2:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_L2;
                    break;
                case KeyEvent.KEYCODE_BUTTON_R2:
                    index = BsGamePadController.GAMEBUTTON_BUTTON_R2;
                    break;
                default:
                    return true;
            }

            if (HANDLE_D1_ONLY){
                if ((mDownEventState & ( 0x1 << BsGamePadController.DIRECTION_PAD_L)) == 0){
                    mDownEventState &= ~(0x1 << index);
                }
            }

            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event){
            if (DEBUG){
                Log.d(TAG, "InputView : onTouchEvent : event = " + event);
            }

            if (event.getAction() != MotionEvent.ACTION_MOVE){
                return true;
            }


            int directionCount = BsGamePadController.DIRECTIONPAD_COUNT;
            if (HANDLE_D1_ONLY){
                directionCount = 1;
            }

            for (int i=0;i<directionCount;i++){
/*
				if ((mDownEventState & (0x1 << (ZsXiaojiGameController.DIRECTION_PAD_L+i))) != 0){
					if (mHandler.hasMessages(DIRECTION_PAD_UP)){
						mHandler.removeMessages(DIRECTION_PAD_UP);
					}

					Message msg = mHandler.obtainMessage(DIRECTION_PAD_UP);
					msg.arg1 = ZsXiaojiGameController.DIRECTION_PAD_L+i;
					mHandler.sendMessageDelayed(msg, DIRECTION_PAD_UP_TIMEOUT);
					continue;
				}
*/

                String directionName = "D1";
                int axis_X = MotionEvent.AXIS_X;
                int axis_Y = MotionEvent.AXIS_Y;
                if (i ==
                    (BsGamePadController.DIRECTION_PAD_R - BsGamePadController.DIRECTION_PAD_L)){
                    directionName = "D2";
                    axis_X = MotionEvent.AXIS_Z;
                    axis_Y = MotionEvent.AXIS_RZ;
                } else if (i ==
                    (BsGamePadController.DIRECTION_PAD_HAT - BsGamePadController.DIRECTION_PAD_L)){
                    directionName = "HAT";
                    axis_X = MotionEvent.AXIS_HAT_X;
                    axis_Y = MotionEvent.AXIS_HAT_Y;
                }

                float X = BsGamePadController.getCenteredAxis(event, event.getDevice(), axis_X, -1);
                float Y = BsGamePadController.getCenteredAxis(event, event.getDevice(), axis_Y, -1);
                boolean isDirectionPressed =
                    (Float.compare(X, 0.0f) != 0) || (Float.compare(Y, 0.0f) != 0);
                if (isDirectionPressed){
                    if ((mDownEventState & (0x1 << (BsGamePadController.DIRECTION_PAD_L+i))) != 0){
                        continue;
                    }

                    if (HANDLE_D1_ONLY){
                        if ((mDownEventState & BsGamePadController.GAMEBUTTON_MASK) != 0){
                            continue;
                        }
                    }

                    if (mDownEventState == 0){
                        mTouchMask = 0;
                        mInputStringList.clear();
                    }

                    int index;
                    for (index=0;index<mInputStringList.size();index++){
                        if (mInputStringList.get(index).equals(directionName)){
                            break;
                        }
                    }

                    if (index == mInputStringList.size()){
                        mInputStringList.add(directionName);
                    }

                    mDownEventState |= (0x1 << (BsGamePadController.DIRECTION_PAD_L+i));
                    mTouchMask |= (0x1 << (BsGamePadController.DIRECTION_PAD_L+i));

                    Message msg = mHandler.obtainMessage(DIRECTION_PAD_UP);
                    msg.arg1 = (BsGamePadController.DIRECTION_PAD_L+i);
                    mHandler.sendMessageDelayed(msg, DIRECTION_PAD_UP_TIMEOUT);

                    StringBuilder strBuilder = new StringBuilder();
                    for (index=0;index<mInputStringList.size();index++){
                        strBuilder.append(mInputStringList.get(index)+"+");
                    }

                    if (strBuilder.length() != 0){
                        strBuilder.deleteCharAt(strBuilder.length()-1);
                    }

                    setText(strBuilder);
                    setTag(mTouchMask);
                } else {
                    if ((mDownEventState & (0x1 << (BsGamePadController.DIRECTION_PAD_L+i))) == 0){
                        continue;
                    }

                    mDownEventState &= ~(0x1 << (BsGamePadController.DIRECTION_PAD_L+i));
                }
            }

            return true;
        }
    }

    private class CircleTextView extends TextView {
        int lastX = 0;
        int lastY = 0;
        CircleTextView(Context context){
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (DEBUG){
                //Log.d(TAG, "getCaller = " + Debug.getCallers(20));
                Log.d(TAG, "onTouchEvent event = " + event);
            }
            switch (event.getAction() & MotionEvent.ACTION_MASK){
                case MotionEvent.ACTION_DOWN:
                    if (mHandler.hasMessages(DOUBLE_TAP_FOR_DEL)){
                        mHandler.removeMessages(DOUBLE_TAP_FOR_DEL);
                        handleDoubleTapForDel(this);
                    } else {
                        mHandler.sendEmptyMessageDelayed(DOUBLE_TAP_FOR_DEL, DOUBLE_TAP_TIMEOUT);
                    }
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastX = (int)event.getX(event.getActionIndex());
                    lastY = (int)event.getY(event.getActionIndex());
                    if (DEBUG){
                        Log.d(TAG, "lastX = " + lastX);
                        Log.d(TAG, "lastY = " + lastY);
                    }

                    break;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)event.getX(event.getActionIndex()) - lastX;
                    int dy = (int)event.getY(event.getActionIndex()) - lastY;

                    int left = getLeft() + dx;
                    int top = getTop() + dy;
                    int right = getRight() + dx;
                    int bottom = getBottom() + dy;

                    if (DEBUG){
                        Log.d(TAG, "BEFORE ADJUSTMENT");
                        Log.d(TAG, "left = " + left);
                        Log.d(TAG, "top = " + top);
                        Log.d(TAG, "right = " + right);
                        Log.d(TAG, "bottom = " + bottom);
                    }

                    if (left < 0){
                        left = 0;
                        right = left + getWidth();
                    }

                    if (right > mScreenWidth) {
                        right = mScreenWidth;
                        left = right - getWidth();
                    }

                    if (top < 0){
                        top = 0;
                        bottom = top + getHeight();
                    }

                    if (bottom > mScreenHeight){
                        bottom = mScreenHeight;
                        top = bottom - getHeight();
                    }

                    if (DEBUG){
                        Log.d(TAG, "mScreenHeight = " + mScreenHeight);
                        Log.d(TAG, "mScreenWidth = " + mScreenWidth);

                        Log.d(TAG, "AFTER ADJUSTMENT");
                        Log.d(TAG, "left = " + left);
                        Log.d(TAG, "top = " + top);
                        Log.d(TAG, "right = " + right);
                        Log.d(TAG, "bottom = " + bottom);
                    }

                    layout(left, top, right, bottom);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    Log.d(TAG, "getFinalPosX = " + event.getRawX());
                    Log.d(TAG, "getFinalPosY = " + event.getRawY());
                    int taskMask = (int)getTag();
                    float r = mMapper.getGamePadMapper().get(taskMask).getRadius();
                    mMapper.getGamePadMapper().put(taskMask,
                        new BsGamePadMapper.ButtonPadMap(event.getRawX(), event.getRawY(), r));
                    break;
                default:
                    break;
            }

            return true;
        }
    }

    private class BsKeyMapperDialog extends AlertDialog {
        private final Context mContext;
        private InputView mInputView;
        private MotionEvent mInputEvent;
        private View mDelView;

        public BsKeyMapperDialog(Context context){
            this(context, 0);
        }

        public void setInputMotionEvent(MotionEvent event){
            mInputEvent = MotionEvent.obtain(event);
        }

        public MotionEvent getInputMotionEvent(){
            return mInputEvent;
        }

        public void setDelView(View view){
            mDelView = view;
        }

        public View getDelView(){
            return mDelView;
        }

        public BsKeyMapperDialog(Context context, int theme){
            super(context, theme);
            mContext = context;
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            if (DEBUG){
                Log.d(TAG, "LayoutParams = " + attrs.toString());
            }

            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().getAttributes().token = mService.getActivityToken();

            attrs = getWindow().getAttributes();
            if (DEBUG){
                Log.d(TAG, "LayoutParams = " + attrs.toString());
            }

            setCancelable(false);
            mInputView = null;
        }

        public void enableInputDetect(boolean enable){
            if (enable){
                mInputView = new InputView(mContext);
                setView(mInputView);
            }
        }

        public View getView(){
            return mInputView;
        }

        public void setPositiveButton(CharSequence text, final OnClickListener listener){
            setButton(BUTTON_POSITIVE, text, listener);
        }

        public void setNegativeButton(CharSequence text, final OnClickListener listener){
            setButton(BUTTON_NEGATIVE, text, listener);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event){
            switch(keyCode){
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                case KeyEvent.KEYCODE_BUTTON_START:
                    break;
                default:
                    if (mInputView != null){
                        mInputView.onKeyDown(keyCode, event);
                    }
                    break;
            }

            return true;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event){
            switch(keyCode){
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    getButton(BUTTON_NEGATIVE).performClick();
                    break;
                case KeyEvent.KEYCODE_BUTTON_START:
                    getButton(BUTTON_POSITIVE).performClick();
                    break;
                default:
                    if (mInputView != null){
                        mInputView.onKeyUp(keyCode, event);
                    }
            }

            return true;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event){
            if (mInputView != null){
                mInputView.onTouchEvent(event);
            }

            return true;
        }
    }
}
