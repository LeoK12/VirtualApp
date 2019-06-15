package com.blackshark.gamepadservice;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadController {
    private static final String TAG = "BsGamePadController";
    private static final boolean DEBUG = true;

    private static final boolean DEBUG_FOR_MULTIKEY_CONFLICT = false;
    private static final int TYPE_SEND_MOVE_UP = 0;
    private static final int MOVE_DOWN_UP_TIMEOUT = 30;
    private static final int BACKUP_HOOK_TIMEOUT = 70;
    private static final int TYPE_BACKUP_HOOK = 1;
    private static final int TYPE_INJECT_EVENT = 2;

    public static final int GAMEBUTTON_START     = 0;
    public static final int GAMEBUTTON_SELECT    = 1;
    public static final int GAMEBUTTON_BUTTON_A  = 2;
    public static final int GAMEBUTTON_BUTTON_B  = 3;
    public static final int GAMEBUTTON_BUTTON_X  = 4;
    public static final int GAMEBUTTON_BUTTON_Y  = 5;
    public static final int GAMEBUTTON_BUTTON_L1 = 6;
    public static final int GAMEBUTTON_BUTTON_R1 = 7;
    public static final int GAMEBUTTON_BUTTON_L2 = 8;
    public static final int GAMEBUTTON_BUTTON_R2 = 9;
    public static final int GAMEBUTTON_COUNT     = 10;
    public static final int GAMEBUTTON_MASK      = ((0x1 << GAMEBUTTON_COUNT) - 1);

    public static final int DIRECTIONPAD_COUNT = 3;
    public static final int DIRECTION_PAD_L     = 10;
    public static final int DIRECTION_PAD_R     = 11;
    public static final int DIRECTION_PAD_HAT   = 12;
    public static final int GAMEPAD_CONTROL_COUNT = 13;

    public static final int INVALID_POINTER_ID      = -1;
    public static final int MAX_POINTER_COUNT       = 16;
    public static final int MAX_POINTER_ID_COUNT    = 32;

    private long mGameButtonStatesBitMap = 0;
    private Pos[] mTouchList;

    private Context mContext;
    private BsGamePadMapper mGamePadMapper;
    private BsGamePadKeyMapView mKeyMapView;
    private BsGamePadService mGameService;
    private WindowManager mWindowManager;
    private int mMainDirectionPad;

    private InputEvent mCurrentEvent;
    private InjectMotionEventThread mInjectMotionEventThread;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case TYPE_SEND_MOVE_UP:
                    int actionIndex = msg.arg1;
                    Pos upPos = (Pos)msg.obj;
                    InjectMotionEvent event = buildMotionEvent(actionIndex, MotionEvent.ACTION_UP);
                    fire(event);
                    mGameButtonStatesBitMap &= ~(0x1 << msg.arg2);
                    break;
                case TYPE_BACKUP_HOOK:
                    mGameService.backupMethod();
                    break;
                case TYPE_INJECT_EVENT:
                    MotionEvent motionEvent = (MotionEvent)msg.obj;
                    mGameService.injectInputEvent(motionEvent, true);
                    break;
                default:
                    break;
            }
        }
    };

    protected static float getCenteredAxis(MotionEvent event, InputDevice device, int axis, int historyPos){
        if (device == null || event == null){
            return 0;
        }

        final InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value = historyPos < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    private class Pos {
        private int touchId;
        private float x;
        private float y;
        private boolean fromTouch;

        public Pos() {
            reset();
        }

        public Pos(float x, float y) {
            this.fromTouch = false;
            this.touchId = -1;
            this.x = x;
            this.y = y;
        }

        public Pos(float x, float y, int pointerId) {
            this.fromTouch = false;
            this.touchId = -1;
            this.x = x;
            this.y = y;
        }

        public void reset() {
            this.fromTouch = false;
            this.touchId = -1;
            this.x = -1;
            this.y = -1;
        }

        public boolean isValid() {
            return touchId != -1;
        }

        public boolean equals(Pos pos) {
            return (this.x == pos.x && this.y == pos.y && this.touchId == pos.touchId);
        }

        public void setX(float x) {
            this.x = x;
        }

        public void setY(float y) {
            this.y = y;
        }

        public void setTouchId(int touchId) {
            this.touchId = touchId;
        }

        public void setFromTouch(boolean touch) {
            this.fromTouch = touch;
        }

        public boolean getFromTouch() {
            return fromTouch;
        }

        public void setPos(Pos pos) {
            this.fromTouch = pos.getFromTouch();
            this.touchId = pos.getTouchId();
            this.x = pos.getX();
            this.y = pos.getY();
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public int getTouchId() {
            return touchId;
        }

        public String toString() {
            return String.format("Pos {x = %f, y = %f, touchId = %d}", x, y, touchId);
        }
    }

    public BsGamePadController(Context context, BsGamePadMapper mapper, BsGamePadService service) {
        mContext = context;
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mTouchList = new Pos[MAX_POINTER_COUNT];
        for (int i=0;i<MAX_POINTER_COUNT;i++){
            mTouchList[i] = new Pos();
        }
        mGamePadMapper = mapper;
        mGameService = service;
        mMainDirectionPad = DIRECTION_PAD_L;
        mInjectMotionEventThread = new InjectMotionEventThread();
        mInjectMotionEventThread.start();
    }


    public void addKeyMapView(){
        mHandler.sendEmptyMessageDelayed(TYPE_BACKUP_HOOK, BACKUP_HOOK_TIMEOUT);
        WindowManager.LayoutParams innerLp = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT);
        //        if (ActivityManager.isHighEndGfx()) {
        innerLp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        //            innerLp.privateFlags |=
        //                WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
        //
        //        }
        //        innerLp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_POINTER_GESTURES;
        if (mKeyMapView == null){
            mKeyMapView = new BsGamePadKeyMapView(mContext, mGamePadMapper, mGameService);
        }
        innerLp.token = mGameService.getBinder();
        Log.d(TAG, "innerLp.token = " + innerLp.token);
        mKeyMapView.setFocusable(true);
        mKeyMapView.setFocusableInTouchMode(true);
        mKeyMapView.requestFocus();
        mKeyMapView.setShow(true);
        mWindowManager.addView(mKeyMapView, innerLp);
    }

    private boolean isKeyMapViewVisible(){
        if (mKeyMapView == null){
            return false;
        }

        return mKeyMapView.getShow();
    }

    public void removeKeyMapView(){
        if (mKeyMapView != null) {
            mGamePadMapper.syncGamePadMapper();
            if (mKeyMapView.getShow()){
                mWindowManager.removeView(mKeyMapView);
            }
            mKeyMapView = null;
            if (DEBUG) {
                Log.d(TAG, "removeKeyMapView : mKeyMapView = " + mKeyMapView);
            }
        }
    }

    public boolean processInputEvent(InputEvent event){
        if (DEBUG) Log.d(TAG, "event = " + event);
        if (DEBUG) Log.d(TAG, "onInputEvent = " + event.toString());
        boolean isHandled = false;
        if (((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0)
            || ((event.getSource() & InputDevice.SOURCE_GAMEPAD) != 0)
            || ((event.getSource() & InputDevice.SOURCE_TOUCHSCREEN) != 0)
            || ((event.getSource() & InputDevice.SOURCE_KEYBOARD) != 0)) {
            // input device is joystick
//                mCurrentEvent = MotionEvent.obtain((MotionEvent) event);
            isHandled = processJoystickEvent(event);
        }

        return isHandled;
    }

    private boolean processJoystickEvent(InputEvent event) {
        if (event instanceof KeyEvent) {
            if (event.getSource() == (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_KEYBOARD) &&
                !isKeyMapViewVisible()){
                int action = ((KeyEvent)event).getAction();
                int keyCode = ((KeyEvent)event).getKeyCode();
                if (action == KeyEvent.ACTION_UP){
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT){
                        mGameButtonStatesBitMap = 0;
                        for (int i=0;i<MAX_POINTER_COUNT;i++){
                            removeTouch(i);
                        }
                        addKeyMapView();
                        return true;
                    }
                }
                return processGameButtonEvent((KeyEvent) event);
            } else {
                return false;
            }
        } else if (event instanceof MotionEvent) {
            if (!isKeyMapViewVisible()) {
                if ((event.getSource() & InputDevice.SOURCE_TOUCHSCREEN) != 0){
                    return processTouchScreenEvent((MotionEvent) event);
                } else {
                    return processJoystickMotionEvent((MotionEvent) event);
                }
            } else {
                return false;
            }
        }

        return false;
    }


    private void updateTouchId(int index, int touchid){
        mTouchList[index].setTouchId(touchid);
    }

    private int insertTouch(Pos pos){
        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (mTouchList[i].getTouchId() == pos.getTouchId()){
                mTouchList[i].setPos(pos);
                return i;
            }
        }

        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (!mTouchList[i].isValid()){
                mTouchList[i].setPos(pos);
                return i;
            }
        }

        return -1;
    }

    private boolean removeTouch(Pos pos){
        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (mTouchList[i].equals(pos)){
                mTouchList[i].reset();
                return true;
            }
        }

        return false;
    }

    private boolean removeTouch(int actionIndex){
        if (actionIndex < 0 || actionIndex >= MAX_POINTER_COUNT){
            return false;
        }

        mTouchList[actionIndex].reset();
        return true;
    }

    private boolean processTouchScreenEvent(MotionEvent event){
        int count = event.getPointerCount();
        int actionIndex = INVALID_POINTER_ID;
        for (int i=0;i<count;i++){
            Pos pos = new Pos(event.getX(i), event.getY(i));
            pos.setFromTouch(true);
            pos.setTouchId(mGamePadMapper.getGamePadMapper().size()+event.getPointerId(i));
            if (i == event.getActionIndex()){
                actionIndex = insertTouch(pos);
            } else {
                insertTouch(pos);
            }
        }

        InjectMotionEvent motionEvent = buildMotionEvent(actionIndex,
            event.getAction()&MotionEvent.ACTION_MASK);
        fire(motionEvent);
        return true;
    }

    private boolean processGameButtonEvent(KeyEvent event) {
        int index = -1;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP){
            return false;
        }

        switch (event.getKeyCode())
        {
            case KeyEvent.KEYCODE_BUTTON_START:
                index = GAMEBUTTON_START;
                break;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                index = GAMEBUTTON_SELECT;
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
                index = GAMEBUTTON_BUTTON_A;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                index = GAMEBUTTON_BUTTON_B;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                index = GAMEBUTTON_BUTTON_X;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                index = GAMEBUTTON_BUTTON_Y;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                index = GAMEBUTTON_BUTTON_L1;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                index = GAMEBUTTON_BUTTON_R1;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                index = GAMEBUTTON_BUTTON_L2;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                index = GAMEBUTTON_BUTTON_R2;
                break;
            default:
                return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN){
            mGameButtonStatesBitMap |= (0x1 << index);
        }

        if (DEBUG){
            Log.d(TAG, "mGameButtonStatesBitMap = " + mGameButtonStatesBitMap);
            for (int i=0;i<mGamePadMapper.getGamePadMapper().size();i++){
                Log.d(TAG, "touchMask = " + mGamePadMapper.getGamePadMapper().keyAt(i) +
                    " buttonMap = " + mGamePadMapper.getGamePadMapper().valueAt(i).toString());
            }
        }

        int touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)(mGameButtonStatesBitMap));
        if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
            touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)(mGameButtonStatesBitMap&GAMEBUTTON_MASK));
            if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)(0x1 << index));
                if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                    touchid = -1;
                }
            }
        }

        if (touchid != -1) {
            if (DEBUG_FOR_MULTIKEY_CONFLICT){
                if (event.getAction() == KeyEvent.ACTION_DOWN){
                    long tmpGameButtonStatesBitMap = mGameButtonStatesBitMap & ~(0x1 << index);
                    int previd = mGamePadMapper.getGamePadMapper().indexOfKey((int)(tmpGameButtonStatesBitMap&GAMEBUTTON_MASK));
                    if (previd >=0 && previd < mGamePadMapper.getGamePadMapper().size()){
                        Pos pos = new Pos(
                            mGamePadMapper.getGamePadMapper().valueAt(previd).getCenterX(),
                            mGamePadMapper.getGamePadMapper().valueAt(previd).getCenterY());
                        pos.setTouchId(previd);
                        int actionIndex = insertTouch(pos);
                        InjectMotionEvent motionEvent = buildMotionEvent(actionIndex, MotionEvent.ACTION_CANCEL);
                        fire(motionEvent);
                    }
                }
            }

            Pos pos = new Pos(
                mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterX(),
                mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterY());
            pos.setTouchId(touchid);
            int actionIndex = insertTouch(pos);
            InjectMotionEvent motionEvent = buildMotionEvent(actionIndex, event.getAction());
            fire(motionEvent);
        }

        if (event.getAction() == KeyEvent.ACTION_UP){
            mGameButtonStatesBitMap &= ~(0x1 << index);
        }

        return touchid != -1 ? true : false;
    }

    private boolean processJoystickMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            final int historySize = event.getHistorySize();
            for (int i = 0; i < historySize; i++) {
                processJoystickDirectPadEvent(event, i);
            }
            processJoystickDirectPadEvent(event, -1);
        }
        return true;
    }

    private Pos getPosFromMotionEvent(MotionEvent event, int historyPos, int index){
        if (index < DIRECTION_PAD_L && index > DIRECTION_PAD_HAT){
            return null;
        }

        if (DEBUG){
            Log.d(TAG, "index = " + index);
        }
        int axis_X = MotionEvent.AXIS_X;
        int axis_Y = MotionEvent.AXIS_Y;
        if (index == DIRECTION_PAD_R){
            axis_X = MotionEvent.AXIS_Z;
            axis_Y = MotionEvent.AXIS_RZ;
        } else if (index == DIRECTION_PAD_HAT){
            axis_X = MotionEvent.AXIS_HAT_X;
            axis_Y = MotionEvent.AXIS_HAT_Y;
        }

        return new Pos(
            getCenteredAxis(event, event.getDevice(), axis_X, historyPos),
            getCenteredAxis(event, event.getDevice(), axis_Y, historyPos));
    }

    private void processJoystickDirectPadEvent(MotionEvent event, int historyPos) {
        int touchid = -1;
        for (int i=0;i<DIRECTIONPAD_COUNT;i++){
            Pos pos = getPosFromMotionEvent(event, historyPos, DIRECTION_PAD_L+i);
            if (pos == null){
                continue;
            }

            boolean isDirectionPressed =
                (Float.compare(pos.x, 0.0f) != 0) || (Float.compare(pos.y, 0.0f) != 0);
            if (!isDirectionPressed) {
                if (mMainDirectionPad != (DIRECTION_PAD_L+i)){
                    continue;
                }
                if ((mGameButtonStatesBitMap & (0x1 << (DIRECTION_PAD_L+i))) == 0){
                    continue;
                }

                long touchMask =
                    ((0x1 << (DIRECTION_PAD_L+i)) | (mGameButtonStatesBitMap & GAMEBUTTON_MASK));
                touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)touchMask);
                if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                    touchid = mGamePadMapper.getGamePadMapper().indexOfKey(0x1 << (DIRECTION_PAD_L+i));
                    if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                        touchid = -1;
                    }
                }

                if (touchid == -1){
                    continue;
                }

                Pos upPos = new Pos(
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterX(),
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterY());
                upPos.setTouchId(touchid);
                int actionIndex = insertTouch(upPos);

                if (mHandler.hasMessages(TYPE_SEND_MOVE_UP)){
                    mHandler.removeMessages(TYPE_SEND_MOVE_UP);
                }
                Message msg = mHandler.obtainMessage(TYPE_SEND_MOVE_UP);
                msg.arg1 = actionIndex;
                msg.arg2 = DIRECTION_PAD_L+i;
                msg.obj = upPos;
                mHandler.sendMessageDelayed(msg, MOVE_DOWN_UP_TIMEOUT);
            } else {
                if (mHandler.hasMessages(TYPE_SEND_MOVE_UP)){
                    mHandler.removeMessages(TYPE_SEND_MOVE_UP);
                }

                if (mMainDirectionPad == (DIRECTION_PAD_L+i)){
                    if ((mGameButtonStatesBitMap & (0x1 << (DIRECTION_PAD_L+i))) == 0){
                        mGameButtonStatesBitMap |= (0x1 << (DIRECTION_PAD_L+i));
                        touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)mGameButtonStatesBitMap);
                        if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                            touchid = mGamePadMapper.getGamePadMapper().indexOfKey(0x1 << (DIRECTION_PAD_L+i));
                            if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                                touchid = -1;
                            }
                        }

                        if (touchid == -1){
                            if (DEBUG){
                                Log.e(TAG, "something is wrong in ZsGamePadService");
                                Log.e(TAG, "mGameButtonStatesBitMap = " + Integer.toBinaryString((int)mGameButtonStatesBitMap));
                                for (int j=0;j<MAX_POINTER_COUNT;j++){
                                    Log.e(TAG, "mTouchList["+ j +"] = " + mTouchList[j].toString());
                                }
                            }
                            continue;
                        }

                        Pos downPos = new Pos(
                            mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterX(),
                            mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterY());
                        downPos.setTouchId(touchid);
                        int actionIndex = insertTouch(downPos);

                        InjectMotionEvent motionEvent = buildMotionEvent(actionIndex, MotionEvent.ACTION_DOWN);
                        fire(motionEvent);
                    }
                }

                if (touchid == -1){
                    touchid = mGamePadMapper.getGamePadMapper().indexOfKey((int)mGameButtonStatesBitMap);
                    if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                        if (mMainDirectionPad == (DIRECTION_PAD_L+i)){
                            touchid = mGamePadMapper.getGamePadMapper().indexOfKey(0x1 << (DIRECTION_PAD_L+i));
                        } else {
                            touchid = mGamePadMapper.getGamePadMapper().
                                indexOfKey((int)(mGameButtonStatesBitMap&GAMEBUTTON_MASK));
                        }

                        if (touchid < 0 || touchid >= mGamePadMapper.getGamePadMapper().size()){
                            touchid = -1;
                        }
                    }
                }

                if (touchid == -1){
                    continue;
                }

                pos.setX((pos.getX()*
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getRadius()) +
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterX());

                pos.setY((pos.getY()*
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getRadius()) +
                    mGamePadMapper.getGamePadMapper().valueAt(touchid).getCenterY());

                pos.setTouchId(touchid);
                int actionIndex = insertTouch(pos);

                InjectMotionEvent motionEvent = buildMotionEvent(actionIndex, MotionEvent.ACTION_MOVE);
                fire(motionEvent);
            }
        }
    }

    private InjectMotionEvent buildMotionEvent(int actionIndex, int action) {
        if (DEBUG){
            Log.d(TAG, "buildMotionEvent " + actionIndex);
            if (actionIndex < 0 && actionIndex >= MAX_POINTER_COUNT){
                Log.e(TAG, "something is wrong in ZsGamePadService");
                Log.e(TAG, "mGameButtonStatesBitMap = " + Integer.toBinaryString((int)mGameButtonStatesBitMap));
                for (int i=0;i<MAX_POINTER_COUNT;i++){
                    Log.e(TAG, "mTouchList["+ i +"] = " + mTouchList[i].toString());
                }

                return null;
            }
        }
        int nCount = 0;
        boolean fromTouch = false;
        ArrayList<MotionEvent.PointerCoords> pointerCoordsList =
            new ArrayList<MotionEvent.PointerCoords>();
        ArrayList<MotionEvent.PointerProperties> pointerPropsList =
            new ArrayList<MotionEvent.PointerProperties>();

        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (!mTouchList[i].isValid()){
                continue;
            }

            MotionEvent.PointerProperties pointerProps = new MotionEvent.PointerProperties();
            pointerProps.id = i;
            pointerProps.toolType = MotionEvent.TOOL_TYPE_FINGER;
            pointerPropsList.add(pointerProps);

            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            pointerCoords.x = mTouchList[i].getX();
            pointerCoords.y = mTouchList[i].getY();
            pointerCoords.pressure = 1.0f;
            pointerCoords.size =  1.0f;
            pointerCoordsList.add(pointerCoords);
            nCount++;
        }

        fromTouch = mTouchList[actionIndex].getFromTouch();

        if (action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_POINTER_UP ||
            action ==  MotionEvent.ACTION_CANCEL){
            removeTouch(actionIndex);
        }

        if (nCount == 0){
            if (DEBUG){
                Log.d(TAG, "buildMotionEvent " + actionIndex);
                Log.e(TAG, "something is wrong in ZsGamePadService");
                Log.e(TAG, "mGameButtonStatesBitMap = " + Integer.toBinaryString((int)mGameButtonStatesBitMap));
                for (int i=0;i<MAX_POINTER_COUNT;i++){
                    Log.e(TAG, "mTouchList["+ i +"] = " + mTouchList[i].toString());
                }

                return null;
            }
        }

        if (nCount > 1) {
            if (action != MotionEvent.ACTION_MOVE) {
                switch (action){
                    case MotionEvent.ACTION_DOWN:
                        action = MotionEvent.ACTION_POINTER_DOWN;
                        break;
                    case MotionEvent.ACTION_UP:
                        action = MotionEvent.ACTION_POINTER_UP;
                        break;
                }

                action |= actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            }
        }

        MotionEvent motionEvent = MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            action,
            nCount,
            pointerPropsList.toArray(new MotionEvent.PointerProperties[pointerPropsList.size()]),
            pointerCoordsList.toArray(new MotionEvent.PointerCoords[pointerCoordsList.size()]),
            0, // metaState
            0, // buttonState
            1, // xPrecision
            1, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN, // source
            0  // flags
        );

        boolean waitForFinish = false;
        if (((action&MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN ||
            (action&MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) /* && !fromTouch */ ){
            waitForFinish = true;
        }

        return new InjectMotionEvent(mCurrentEvent, motionEvent, waitForFinish);
    }

    private void fire(InjectMotionEvent injectMotionEvent) {
        mInjectMotionEventThread.inputMotionEvent(injectMotionEvent);
    }

    public InjectMotionEventThread getInjectMotionEventThread(){
        return mInjectMotionEventThread;
    }

    public class InjectMotionEvent{
        public InputEvent orgEvent;
        public MotionEvent injectEvent;
        public boolean waitForFinish;

        InjectMotionEvent(InputEvent orgEvent, MotionEvent injectEvent, boolean waitForFinish){
            this.orgEvent = orgEvent;
            this.injectEvent = injectEvent;
            this.waitForFinish = waitForFinish;
        }
    }

    public class InjectMotionEventThread extends Thread{
        Queue<InjectMotionEvent> motionEventQueue = new ArrayBlockingQueue<InjectMotionEvent>(32);
        Object objWaitForFinish = new Object();

        public void inputMotionEvent(InjectMotionEvent injectMotionEvent){
            synchronized (motionEventQueue){
//                    try {
                motionEventQueue.add(injectMotionEvent);
//                    } catch (Exception e){
//                        e.printStackTrace();
//                        nofityForFinish();
//                    }
                motionEventQueue.notify();
            }
        }

        public void nofityForFinish(){
            synchronized (objWaitForFinish){
                try {
                    objWaitForFinish.notify();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            while (true){
                InjectMotionEvent event;
                synchronized (motionEventQueue){
                    event = motionEventQueue.poll();
                    if (event == null){
                        try {
                            motionEventQueue.wait();
                        } catch (Exception e){
                            e.printStackTrace();
                        }

                        event = motionEventQueue.poll();
                    }
                }

                if (DEBUG) {
                    Log.d(TAG, "InjectMotionEventThread:run event = " + event);
                }

                Message msg = mHandler.obtainMessage(TYPE_INJECT_EVENT);
                msg.obj = event.injectEvent;
                mHandler.sendMessage(msg);
                //mGameService.injectInputEvent(event.injectEvent, true);
                if (event.waitForFinish) {
                    synchronized (objWaitForFinish) {
                        try {
                            objWaitForFinish.wait();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
