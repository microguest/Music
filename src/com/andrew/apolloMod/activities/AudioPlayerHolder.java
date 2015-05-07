package com.andrew.apolloMod.activities;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.*;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio;
import android.support.v4.view.ViewPager;
import android.view.*;
import android.widget.*;
import com.andrew.apolloMod.IApolloService;
import com.andrew.apolloMod.R;
import com.andrew.apolloMod.cache.ImageInfo;
import com.andrew.apolloMod.helpers.utils.ApolloUtils;
import com.andrew.apolloMod.helpers.utils.MusicUtils;
import com.andrew.apolloMod.helpers.utils.ThemeUtils;
import com.andrew.apolloMod.preferences.SettingsHolder;
import com.andrew.apolloMod.service.ApolloService;
import com.andrew.apolloMod.service.ServiceToken;
import com.andrew.apolloMod.ui.adapters.PagerAdapter;
import com.andrew.apolloMod.ui.fragments.AudioPlayerFragmentList;
import com.andrew.apolloMod.ui.fragments.AudioPlayerFragmentLrc;
import com.andrew.apolloMod.ui.fragments.AudioPlayerFragmentView;
import com.andrew.apolloMod.ui.widgets.RepeatingImageButton;

import static com.andrew.apolloMod.Constants.*;

/**
 * @author Andrew Neal
 * @Note This is the "holder" for the @TracksFragment(queue) and @AudioPlayerFragment
 */
public class AudioPlayerHolder extends Activity implements ServiceConnection {

    private ServiceToken mToken;

    // Options
    private static final int FAVORITE = 0;

    private static final int SEARCH = 1;

    private static final int EFFECTS_PANEL = 0;

    private PagerAdapter mPagerAdapter;

    private View virtual_action_bar;

    /* title 文字  和 底部播放控制view start */

    // song Name, album Name, and artist Name
    private TextView mTrackName, mAlbumName, mArtistName;

    // Total and current time
    private TextView mTotalTime, mCurrentTime;

    // Controls
    private ImageButton mRepeat, mPlay, mShuffle;

    private RepeatingImageButton mPrev, mNext;

    // Progress
    private SeekBar mProgress;

    // Where we are in the track
    private long mDuration, mLastSeekEventTime, mPosOverride = -1, mStartSeekPos = 0;

    private boolean mFromTouch, paused = false;

    // Handler
    private static final int REFRESH = 1, UPDATEINFO = 2;

    // Notify if repeat or shuffle changes
    private Toast mToast;

    /* title 文字  和 底部播放控制view end */

    @Override
    protected void onCreate(Bundle icicle) {
        // For the theme chooser and overflow MenuItem
        if (ThemeUtils.overflowLight(this)) {
            setTheme(R.style.Apollo_Holo);
        } else {
            setTheme(R.style.Apollo_Holo_Light);
        }
        // Landscape mode on phone isn't ready
        if (!ApolloUtils.isTablet(this))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Control Media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //设置ActionBar 浮动到view 上层来
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        // Layout
        setContentView(R.layout.audio_player_browser);

        //初始化title文字和底部播放控制view
        initTitleBottomView();

        initVirtualActionBar();

        // Set up the ActionBar
        initActionBar();

        // Important!
        initPager();
        super.onCreate(icicle);
    }

    /**
     * 初始化title文字和底部播放控制view
     */
    private void initTitleBottomView() {
        mTrackName = (TextView) findViewById(R.id.audio_player_track);
        mTrackName.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                tracksBrowser();
            }
        });
        mAlbumName = (TextView) findViewById(R.id.audio_player_album);
        mAlbumName.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                tracksBrowserArtist();
            }
        });
        mArtistName = (TextView) findViewById(R.id.audio_player_artists);
        mArtistName.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                tracksBrowserArtist();
            }
        });

        mTotalTime = (TextView) findViewById(R.id.audio_player_total_time);
        mCurrentTime = (TextView) findViewById(R.id.audio_player_current_time);

        mRepeat = (ImageButton) findViewById(R.id.audio_player_repeat);
        mPrev = (RepeatingImageButton) findViewById(R.id.audio_player_prev);
        mPlay = (ImageButton) findViewById(R.id.audio_player_play);
        mNext = (RepeatingImageButton) findViewById(R.id.audio_player_next);
        mShuffle = (ImageButton) findViewById(R.id.audio_player_shuffle);

        mRepeat.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                cycleRepeat();
            }
        });

        mPrev.setRepeatListener(mRewListener, 260);
        mPrev.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (MusicUtils.mService == null)
                    return;
                try {
                    if (MusicUtils.mService.position() < 2000) {
                        MusicUtils.mService.prev();
                    } else {
                        MusicUtils.mService.seek(0);
                        MusicUtils.mService.play();
                    }
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        });

        mPlay.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                doPauseResume();
            }
        });

        mNext.setRepeatListener(mFfwdListener, 260);
        mNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (MusicUtils.mService == null)
                    return;
                try {
                    MusicUtils.mService.next();
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        });

        mShuffle.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                toggleShuffle();
            }
        });

        mProgress = (SeekBar) findViewById(android.R.id.progress);
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        // Theme chooser
        ThemeUtils.setImageButton(AudioPlayerHolder.this, mPrev, "apollo_previous");
        ThemeUtils.setImageButton(AudioPlayerHolder.this, mNext, "apollo_next");
        ThemeUtils.setProgessDrawable(AudioPlayerHolder.this, mProgress, "apollo_seekbar_background");
    }

    //------------------------------------------------------------------------
    /**
     * Update everything as the meta or playstate changes
     */
    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ApolloService.META_CHANGED))
                mHandler.sendMessage(mHandler.obtainMessage(UPDATEINFO));
            setPauseButtonImage();
            setShuffleButtonImage();
            setRepeatButtonImage();
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        paused = true;
        mHandler.removeMessages(REFRESH);
        AudioPlayerHolder.this.unregisterReceiver(mStatusListener);
    }

    /**
     * Cycle repeat states
     */
    private void cycleRepeat() {
        if (MusicUtils.mService == null) {
            return;
        }
        try {
            int mode = MusicUtils.mService.getRepeatMode();
            if (mode == ApolloService.REPEAT_NONE) {
                MusicUtils.mService.setRepeatMode(ApolloService.REPEAT_ALL);
                ApolloUtils.showToast(R.string.repeat_all, mToast, AudioPlayerHolder.this);
            } else if (mode == ApolloService.REPEAT_ALL) {
                MusicUtils.mService.setRepeatMode(ApolloService.REPEAT_CURRENT);
                if (MusicUtils.mService.getShuffleMode() != ApolloService.SHUFFLE_NONE) {
                    MusicUtils.mService.setShuffleMode(ApolloService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
                ApolloUtils.showToast(R.string.repeat_one, mToast, AudioPlayerHolder.this);
            } else {
                MusicUtils.mService.setRepeatMode(ApolloService.REPEAT_NONE);
                ApolloUtils.showToast(R.string.repeat_off, mToast, AudioPlayerHolder.this);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Scan backwards
     */
    private final RepeatingImageButton.RepeatListener mRewListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(View v, long howlong, int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };

    /**
     * Play and pause music
     */
    private void doPauseResume() {
        try {
            if (MusicUtils.mService != null) {
                if (MusicUtils.mService.isPlaying()) {
                    MusicUtils.mService.pause();
                } else {
                    MusicUtils.mService.play();
                }
            }
            refreshNow();
            setPauseButtonImage();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Scan forwards
     */
    private final RepeatingImageButton.RepeatListener mFfwdListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(View v, long howlong, int repcnt) {
            scanForward(repcnt, howlong);
        }
    };

    /**
     * Set the shuffle mode
     */
    private void toggleShuffle() {
        if (MusicUtils.mService == null) {
            return;
        }
        try {
            int shuffle = MusicUtils.mService.getShuffleMode();
            if (shuffle == ApolloService.SHUFFLE_NONE) {
                MusicUtils.mService.setShuffleMode(ApolloService.SHUFFLE_NORMAL);
                if (MusicUtils.mService.getRepeatMode() == ApolloService.REPEAT_CURRENT) {
                    MusicUtils.mService.setRepeatMode(ApolloService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                ApolloUtils.showToast(R.string.shuffle_on, mToast, AudioPlayerHolder.this);
            } else if (shuffle == ApolloService.SHUFFLE_NORMAL
                    || shuffle == ApolloService.SHUFFLE_AUTO) {
                MusicUtils.mService.setShuffleMode(ApolloService.SHUFFLE_NONE);
                ApolloUtils.showToast(R.string.shuffle_off, mToast, AudioPlayerHolder.this);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    private void scanBackward(int repcnt, long delta) {
        if (MusicUtils.mService == null)
            return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = MusicUtils.mService.position();
                mLastSeekEventTime = 0;
            } else {
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    MusicUtils.mService.prev();
                    long duration = MusicUtils.mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    MusicUtils.mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    private void scanForward(int repcnt, long delta) {
        if (MusicUtils.mService == null)
            return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = MusicUtils.mService.position();
                mLastSeekEventTime = 0;
            } else {
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = MusicUtils.mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    MusicUtils.mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    MusicUtils.mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Set the repeat images
     */
    private void setRepeatButtonImage() {
        if (MusicUtils.mService == null)
            return;
        try {
            switch (MusicUtils.mService.getRepeatMode()) {
                case ApolloService.REPEAT_ALL:
                    mRepeat.setImageResource(R.drawable.apollo_holo_light_repeat_all);
                    break;
                case ApolloService.REPEAT_CURRENT:
                    mRepeat.setImageResource(R.drawable.apollo_holo_light_repeat_one);
                    break;
                default:
                    mRepeat.setImageResource(R.drawable.apollo_holo_light_repeat_normal);
                    // Theme chooser
                    ThemeUtils.setImageButton(AudioPlayerHolder.this, mRepeat, "apollo_repeat_normal");
                    break;
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Set the shuffle images
     */
    private void setShuffleButtonImage() {
        if (MusicUtils.mService == null)
            return;
        try {
            switch (MusicUtils.mService.getShuffleMode()) {
                case ApolloService.SHUFFLE_NONE:
                    mShuffle.setImageResource(R.drawable.apollo_holo_light_shuffle_normal);
                    // Theme chooser
                    ThemeUtils.setImageButton(AudioPlayerHolder.this, mShuffle, "apollo_shuffle_normal");
                    break;
                case ApolloService.SHUFFLE_AUTO:
                    mShuffle.setImageResource(R.drawable.apollo_holo_light_shuffle_on);
                    break;
                default:
                    mShuffle.setImageResource(R.drawable.apollo_holo_light_shuffle_on);
                    break;
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Set the play and pause image
     */
    private void setPauseButtonImage() {
        try {
            if (MusicUtils.mService != null && MusicUtils.mService.isPlaying()) {
                mPlay.setImageResource(R.drawable.apollo_holo_light_pause);
                // Theme chooser
                ThemeUtils.setImageButton(AudioPlayerHolder.this, mPlay, "apollo_pause");
            } else {
                mPlay.setImageResource(R.drawable.apollo_holo_light_play);
                // Theme chooser
                ThemeUtils.setImageButton(AudioPlayerHolder.this, mPlay, "apollo_play");
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * @param delay
     */
    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    /**
     * We need to refresh the time via a Handler
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                case UPDATEINFO:
                    updateMusicInfo();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Drag to a specfic duration
     */
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (MusicUtils.mService == null))
                return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    MusicUtils.mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }

                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };

    /**
     * @return current time
     */
    private long refreshNow() {
        if (MusicUtils.mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? MusicUtils.mService.position() : mPosOverride;
            long remaining = 1000 - (pos % 1000);
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(AudioPlayerHolder.this, pos / 1000));

                if (MusicUtils.mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                    mCurrentTime.setTextColor(getResources().getColor(R.color.transparent_white));
                    // Theme chooser
                    ThemeUtils.setTextColor(AudioPlayerHolder.this, mCurrentTime, "audio_player_text_color");
                } else {
                    // blink the counter
                    int col = mCurrentTime.getCurrentTextColor();
                    mCurrentTime.setTextColor(col == getResources().getColor(
                            R.color.transparent_white) ? getResources().getColor(
                            R.color.holo_blue_dark) : getResources().getColor(
                            R.color.transparent_white));
                    remaining = 500;
                    // Theme chooser
                    ThemeUtils.setTextColor(AudioPlayerHolder.this, mCurrentTime, "audio_player_text_color");
                }

                mProgress.setProgress((int) (1000 * pos / mDuration));
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(1000);
            }
            return remaining;
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
        return 500;
    }

    /**
     * Update what's playing
     */
    private void updateMusicInfo() {
        if (MusicUtils.mService == null) {
            return;
        }

        String artistName = MusicUtils.getArtistName();
        String albumName = MusicUtils.getAlbumName();
        String trackName = MusicUtils.getTrackName();
        String albumId = String.valueOf(MusicUtils.getCurrentAlbumId());
        mTrackName.setText(trackName);
        mAlbumName.setText(albumName);
        mArtistName.setText(artistName);
        mDuration = MusicUtils.getDuration();
        mTotalTime.setText(MusicUtils.makeTimeString(AudioPlayerHolder.this, mDuration / 1000));

        ImageInfo mInfo = new ImageInfo();
        mInfo.type = TYPE_ALBUM;
        mInfo.size = SIZE_THUMB;
        mInfo.source = SRC_FIRST_AVAILABLE;
        mInfo.data = new String[]{albumId, artistName, albumName};

        // Theme chooser
        ThemeUtils.setTextColor(AudioPlayerHolder.this, mTrackName, "audio_player_text_color");
        ThemeUtils.setTextColor(AudioPlayerHolder.this, mAlbumName, "audio_player_text_color");
        ThemeUtils.setTextColor(AudioPlayerHolder.this, mArtistName, "audio_player_text_color");
        ThemeUtils.setTextColor(AudioPlayerHolder.this, mTotalTime, "audio_player_text_color");

    }

    /**
     * Takes you into the @TracksBrowser to view all of the tracks on the
     * current album
     */
    private void tracksBrowser() {

        String artistName = MusicUtils.getArtistName();
        String albumName = MusicUtils.getAlbumName();
        String albumId = String.valueOf(MusicUtils.getCurrentAlbumId());
        long id = MusicUtils.getCurrentAlbumId();

        Bundle bundle = new Bundle();
        bundle.putString(MIME_TYPE, Audio.Albums.CONTENT_TYPE);
        bundle.putString(ARTIST_KEY, artistName);
        bundle.putString(ALBUM_KEY, albumName);
        bundle.putString(ALBUM_ID_KEY, albumId);
        bundle.putLong(BaseColumns._ID, id);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(AudioPlayerHolder.this, TracksBrowser.class);
        intent.putExtras(bundle);
        AudioPlayerHolder.this.startActivity(intent);
    }

    /**
     * Takes you into the @TracksBrowser to view all of the tracks and albums by the current artist
     */
    private void tracksBrowserArtist() {

        String artistName = MusicUtils.getArtistName();
        long id = MusicUtils.getCurrentArtistId();

        Bundle bundle = new Bundle();
        bundle.putString(MIME_TYPE, Audio.Artists.CONTENT_TYPE);
        bundle.putString(ARTIST_KEY, artistName);
        bundle.putLong(BaseColumns._ID, id);

        ApolloUtils.setArtistId(artistName, id, ARTIST_ID, AudioPlayerHolder.this);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(AudioPlayerHolder.this, TracksBrowser.class);
        intent.putExtras(bundle);
        AudioPlayerHolder.this.startActivity(intent);
    }

    //------------------------------------------------------------------------------

    /**
     * 设置虚拟actionbar高度
     */
    private void initVirtualActionBar() {
        virtual_action_bar = findViewById(R.id.virtual_action_bar);
        Bitmap myBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.apollo_holo_light_search);//获得图片资源
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, myBitmap.getHeight());
        virtual_action_bar.setLayoutParams(ll);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // If an activity is requesting access to this activity, and
        // the activity is in the stack, the the fragments may need
        // be refreshed. Update the page adapter
        if (mPagerAdapter != null) {
            mPagerAdapter.refresh();
        }
        super.onNewIntent(intent);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder obj) {
        MusicUtils.mService = IApolloService.Stub.asInterface(obj);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        MusicUtils.mService = null;
    }

    /**
     * Update the MenuItems in the ActionBar
     */
    private final BroadcastReceiver mMediaStatusReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            invalidateOptionsMenu();
        }

    };

    @Override
    protected void onStart() {
        // Bind to Service
        mToken = MusicUtils.bindToService(this, this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ApolloService.META_CHANGED);
        filter.addAction(ApolloService.PLAYSTATE_CHANGED);

        registerReceiver(mMediaStatusReceiver, filter);

        IntentFilter f = new IntentFilter();
        f.addAction(ApolloService.PLAYSTATE_CHANGED);
        f.addAction(ApolloService.META_CHANGED);
        AudioPlayerHolder.this.registerReceiver(mStatusListener, new IntentFilter(f));

        long next = refreshNow();
        queueNextRefresh(next);

        super.onStart();
    }

    @Override
    protected void onStop() {
        // Unbind
        if (MusicUtils.mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }

        unregisterReceiver(mMediaStatusReceiver);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, FAVORITE, 0, R.string.cd_favorite).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, SEARCH, 0, R.string.cd_search).setIcon(R.drawable.apollo_holo_light_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.overflow_now_playing, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem favorite = menu.findItem(FAVORITE);
        MenuItem search = menu.findItem(SEARCH);
        if (MusicUtils.mService != null && MusicUtils.getCurrentAudioId() != -1) {
            if (MusicUtils.isFavorite(this, MusicUtils.getCurrentAudioId())) {
                favorite.setIcon(R.drawable.apollo_holo_light_favorite_selected);
            } else {
                favorite.setIcon(R.drawable.apollo_holo_light_favorite_normal);
                // Theme chooser
                ThemeUtils.setActionBarItem(this, favorite, "apollo_favorite_normal");
            }
        }
        // Theme chooser
        ThemeUtils.setActionBarItem(this, search, "apollo_search");
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home: {
                Intent intent = new Intent();
                intent.setClass(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                break;
            }
            case FAVORITE: {
                MusicUtils.toggleFavorite();
                invalidateOptionsMenu();
                break;
            }
            case SEARCH: {
                onSearchRequested();
                break;
            }
            case R.id.add_to_playlist: {
                Intent intent = new Intent(INTENT_ADD_TO_PLAYLIST);
                long[] list = new long[1];
                list[0] = MusicUtils.getCurrentAudioId();
                intent.putExtra(INTENT_PLAYLIST_LIST, list);
                startActivity(intent);
                break;
            }
            case R.id.eq: {
//                Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
//                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, MusicUtils.getCurrentAudioId());
//                startActivityForResult(i, EFFECTS_PANEL);

                Toast.makeText(getApplicationContext(), getResources().getString(R.string.test_no_function), Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.play_store: {
                ApolloUtils.shopFor(this, MusicUtils.getArtistName());
                break;
            }
            case R.id.share: {
                shareCurrentTrack();
                break;
            }
            case R.id.settings: {
                startActivityForResult(new Intent(this, SettingsHolder.class), 0);
                break;
            }
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        startActivity(intent);
    }

    private void initActionBar() {
        ApolloUtils.showUpTitleOnly(getActionBar());

        // The ActionBar Title and UP ids are hidden.
        int titleId = Resources.getSystem().getIdentifier("action_bar_title", "id", "android");
        int upId = Resources.getSystem().getIdentifier("up", "id", "android");

        TextView actionBarTitle = (TextView) findViewById(titleId);
        ImageView actionBarUp = (ImageView) findViewById(upId);

        // Theme chooser
        ThemeUtils.setActionBarBackground(this, getActionBar(), "action_bar_background");
        ThemeUtils.setTextColor(this, actionBarTitle, "action_bar_title_color");
        ThemeUtils.initThemeChooser(this, actionBarUp, "action_bar_up", THEME_ITEM_BACKGROUND);
    }

    /**
     * @return Share intent
     * @throws RemoteException
     */
    private String shareCurrentTrack() {
        if (MusicUtils.getTrackName() == null || MusicUtils.getArtistName() == null) {

        }

        Intent shareIntent = new Intent();
        String currentTrackMessage = getResources().getString(R.string.now_listening_to) + " "
                + MusicUtils.getTrackName() + " " + getResources().getString(R.string.by) + " "
                + MusicUtils.getArtistName();

        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentTrackMessage);

        startActivity(Intent.createChooser(shareIntent,
                getResources().getString(R.string.share_track_using)));
        return currentTrackMessage;
    }

    /**
     * Initiate ViewPager and PagerAdapter
     */
    public void initPager() {
        // Initiate PagerAdapter
        mPagerAdapter = new PagerAdapter(getFragmentManager());
        Bundle bundle = new Bundle();
        bundle.putString(MIME_TYPE, Audio.Playlists.CONTENT_TYPE);
        bundle.putLong(BaseColumns._ID, PLAYLIST_QUEUE);

        mPagerAdapter.addFragment(new AudioPlayerFragmentList(bundle));
        mPagerAdapter.addFragment(new AudioPlayerFragmentView());
        mPagerAdapter.addFragment(new AudioPlayerFragmentLrc());

        initCenterView();

        // Initiate ViewPager
        ViewPager mViewPager = (ViewPager) findViewById(R.id.viewPager);
        mViewPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));
        mViewPager.setPageMarginDrawable(R.drawable.viewpager_margin);
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount());
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPagerPageChangeListener());
        mViewPager.setCurrentItem(DEFULT_VIEWPAGE_INDEX);

        // Theme chooser
        ThemeUtils.initThemeChooser(this, mViewPager, "viewpager", THEME_ITEM_BACKGROUND);
        ThemeUtils.setMarginDrawable(this, mViewPager, "viewpager_margin");
    }


    // 默认viewpage显示第几页
    private static final int DEFULT_VIEWPAGE_INDEX = 1;
    // 中间视图
    private ImageView[] mImageViews = null;

    private void initCenterView() {

        mImageViews = new ImageView[mPagerAdapter.getCount()];

        LinearLayout layout = (LinearLayout) findViewById(R.id.viewGroup);
        LinearLayout.LayoutParams params;
        for (int i = 0; i < mPagerAdapter.getCount(); i++) {
            mImageViews[i] = new ImageView(AudioPlayerHolder.this);

            if (0 == i) {
                mImageViews[i].setBackgroundResource(R.drawable.indicator_page);
            } else {
                mImageViews[i].setBackgroundResource(R.drawable.indicator_page_dark);
            }

            params = new LinearLayout.LayoutParams(18, 18);
            params.setMargins(6, 0, 6, 0);
            mImageViews[i].setLayoutParams(params);
            layout.addView(mImageViews[i]);

        }
    }

    class ViewPagerPageChangeListener implements ViewPager.OnPageChangeListener {

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int page, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int page) {
            //更新游标
            for (int i = 0; i < mPagerAdapter.getCount(); i++) {
                if (page == i) {
                    mImageViews[i].setBackgroundResource(R.drawable.indicator_page);
                } else {
                    mImageViews[i].setBackgroundResource(R.drawable.indicator_page_dark);
                }
            }
        }
    }
}