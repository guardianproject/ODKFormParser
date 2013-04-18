package info.guardianproject.odkparser.widgets;

import info.guardianproject.odkparser.Constants.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class ODKSeekBar extends SeekBar implements OnSeekBarChangeListener, OnInfoListener {
	public MediaPlayer mp;
	public MediaRecorder mr;

	public java.io.File recordingFile;

	Context c;
	Handler h = new Handler();
	
	public boolean isPlaying = false;
	public boolean isRecording = false;
	public boolean canPlay = false;
	public boolean canRecord = false;
	
	public byte[] rawAudioData;
	
	private final static String LOG = Logger.UI;

	public void start(Context c) {
		this.c = c;
		
		mr = new MediaRecorder();
		mr.setAudioSource(MediaRecorder.AudioSource.MIC);
		mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

		setOnSeekBarChangeListener(this);
		setProgress(0);
	}
	
	public ODKSeekBar(Context context) {
		super(context);
		start(context);

	}

	public ODKSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		start(context);
	}
	
	private void saveAudio() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			GZIPOutputStream gos = new GZIPOutputStream(baos);
			
			java.io.FileInputStream fis = new java.io.FileInputStream(recordingFile);
			byte[] buf = new byte[1024];
			int b;
			while((b = fis.read(buf)) > 0) {
				gos.write(buf, 0, b);
			}
			
			fis.close();
			gos.finish();
			gos.close();
			
			baos.flush();
			baos.close();
			
			rawAudioData = Base64.encode(baos.toByteArray(), Base64.DEFAULT);
			
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
	}
	
	private void processAudio() {
		mp.stop();
		mp.reset();
		
		try {
			mp.setDataSource(recordingFile.getAbsolutePath());
			mp.prepare();
			
			setMax(mp.getDuration()/1000);
			
		} catch (IllegalArgumentException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (SecurityException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IllegalStateException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	public void init(java.io.File recordingFile) {
		this.recordingFile = recordingFile;
		
		mr.setOutputFile(recordingFile.getAbsolutePath());

		mp = new MediaPlayer();
		mp.setOnInfoListener(this);
		
		h.post(new Runnable() {
			@Override
			public void run() {
				if(isPlaying) {
					if(mp.getCurrentPosition() >= mp.getDuration()) {
						pause();
						mp.seekTo(0);
						return;
					}

					setProgress(mp.getCurrentPosition()/1000);
				}

				h.postDelayed(this, 1000L);
			}

		});
	}
	
	public void setRawAudioData(byte[] rawAudioData) {
		this.rawAudioData = rawAudioData;
		
		// b64-decode, unzip audio bytes and dump it in public
		try {
			java.io.FileOutputStream fos = new java.io.FileOutputStream(recordingFile);
			
			GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.decode(rawAudioData, Base64.DEFAULT)));
			byte[] buf = new byte[1024];
			int b;
			while((b = gzip.read(buf)) > 0) {
				fos.write(buf, 0, b);
			}
			fos.flush();
			fos.close();

			processAudio();
		} catch (IllegalArgumentException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (SecurityException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IllegalStateException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public void play() {
		canRecord = false;
		mp.start();
		isPlaying = true;
	}

	public void pause() {
		canRecord = true;
		mp.pause();
		isPlaying = false;
	}

	public void record() {
		try {
			mr.prepare();
			mr.start();
			
			isRecording = true;
			canPlay = false;
		} catch (IllegalStateException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		
	}
	
	public void stop() {
		canPlay = true;
		mr.stop();
		mr.release();
		isRecording = false;
		
		processAudio();
		saveAudio();
	}
	
	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if(fromUser) {
			mp.seekTo(progress * 1000);
		}

	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}
}
