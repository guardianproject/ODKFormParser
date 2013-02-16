package info.guardianproject.odkparser.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import info.guardianproject.odkparser.Constants;
import info.guardianproject.odkparser.R;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.data.helper.Selection;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ProgressBar;

public class FormWidgetFactory {

	static final String LOG = Constants.Logger.WIDGET_FACTORY;

	public interface WidgetDataController {
		public void onSave();
		public File openRecorderStream();
	}
	
	public static class ODKMediaRecorder implements OnInfoListener {
		File file;
		MediaRecorder mr;
		MediaPlayer mp;
		
		Button record, play_pause;
		SeekBar progress_bar;
		
		ODKView odk_view;
		Handler h;
		
		boolean is_recording = false;
		boolean is_playing = false;
		
		public ODKMediaRecorder(ODKView odk_view) {			
			this.odk_view = odk_view;
			
			record = (Button) this.odk_view.view.findViewById(R.id.widget_record);
			record.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if(is_recording)
						stopRecording();
					else
						startRecording();
				}
			});
			
			play_pause = (Button) this.odk_view.view.findViewById(R.id.widget_play_or_pause);
			play_pause.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if(is_playing)
						pauseRecording();
					else
						playRecording();
				}
			});
			
			progress_bar = (SeekBar) odk_view.answerHolder;
			progress_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					Log.d(LOG, "seekbar progress: " + progress);
					if(fromUser) {
						mp.seekTo(progress * 1000);
					}
					
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {}
				
			});
			progress_bar.setProgress(0);

			mr = new MediaRecorder();
			mr.setAudioSource(MediaRecorder.AudioSource.MIC);
			mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			
			mp = new MediaPlayer();
			mp.setOnInfoListener(this);
			
			h = new Handler();
			h.post(new Runnable() {
				@Override
				public void run() {
					if(is_playing) {
						if(mp.getCurrentPosition() >= mp.getDuration()) {
								pauseRecording();
								mp.seekTo(0);
								return;
						}
						
						int current_position = mp.getCurrentPosition()/1000;
						progress_bar.setProgress(current_position);
					}
					h.postDelayed(this, 1000);
				}
			});
			
		}
		
		public void init() {
			init(null);
		}
		
		public void init(String audio_data) {
			file = ((WidgetDataController) odk_view.c).openRecorderStream();
			
			if(audio_data == null)
				play_pause.setEnabled(false);
			else {
				try {
					FileOutputStream fos = new FileOutputStream(file);
					byte[] gzipped_audio = Base64.decode(audio_data.getBytes(), Base64.DEFAULT);
					GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipped_audio));
					
					byte[] audio_bytes = new byte[1024];
					int b;
					while((b = gzip.read(audio_bytes)) > 0)
						fos.write(audio_bytes, 0, b);
					
					fos.flush();
					fos.close();
					
					processRecording();
				} catch (FileNotFoundException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				} catch (IOException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
				
			}
			
			mr.setOutputFile(file.getAbsolutePath());
		}
		
		public void shutDown() {
			is_playing = false;
			is_recording = false;
			
			mp.release();
			mr.release();
			file.delete();
		}
		
		private void processRecording() {
			mp.stop();
			mp.reset();
			
			try {
				Log.d(LOG, file.getAbsolutePath());

				mp.setDataSource(file.getAbsolutePath());
				mp.prepare();
				
				progress_bar.setMax(mp.getDuration()/1000);
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
			
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				GZIPOutputStream gos = new GZIPOutputStream(baos);
				FileInputStream fis = new FileInputStream(file);
				
				byte[] audio_bytes = new byte[1024];
				int b;
				while((b = fis.read(audio_bytes)) > 0)
					gos.write(audio_bytes, 0, b);
				
				fis.close();
				gos.finish();
				gos.close();
				
				baos.flush();
				baos.close();
				
				odk_view.answer.setValue(Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT));
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		private void playRecording() {
			play_pause.setText("pz");
			record.setEnabled(false);
			
			try {
				mp.start();
				is_playing = true;
				
			} catch (IllegalArgumentException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (SecurityException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IllegalStateException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		private void pauseRecording() {
			play_pause.setText("pl");
			
			mp.pause();
			is_playing = false;
			
			record.setEnabled(true);
		}
		
		private void startRecording() {
			try {
				mr.prepare();
				mr.start();
				
				is_recording = true;
				record.setText("r*");
				
				play_pause.setEnabled(false);
				
			} catch (IllegalStateException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
		}
		
		private void stopRecording() {
			mr.stop();
			mr.release();
			
			is_recording = false;
			record.setText("r");
			
			play_pause.setEnabled(true);
			processRecording();
		}

		@Override
		public boolean onInfo(MediaPlayer mp, int what, int extra) {
			Log.d(LOG, "on info: " + what + "> " + extra);
			return false;
		}
	}

	public static class ODKView {
		View view, answerHolder;
		QuestionDef qd;
		IAnswerData answer;
		ODKMediaRecorder omr;
		Activity c;

		TextView question_text;
		LinearLayout choiceHolder = null;

		List<SelectChoiceWidget> selectChoices = null;
		boolean hasInitialValue = false;

		public ODKView(QuestionDef qd, Activity c, String initialValue) {
			this.c = c;
			this.qd = qd;
			this.omr = null;
			
			this.answer = new UncastData("");
			
			if(initialValue != null)
				hasInitialValue = true;

			switch(qd.getControlType()) {
			case org.javarosa.core.model.Constants.CONTROL_INPUT:
				view = LayoutInflater.from(c).inflate(R.layout.widget_textinput, null);
				if(hasInitialValue)
					answer = new StringData(String.valueOf(initialValue));
				else
					answer = new StringData("");
				
				answerHolder = (TextView) view.findViewById(R.id.widget_edittext);

				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_ONE:
				view = LayoutInflater.from(c).inflate(R.layout.widget_select, null);
				answer = new SelectOneData();
				if(hasInitialValue) {
					Selection selection = qd.getChoices().get(Integer.parseInt(initialValue) - 1).selection();
					((SelectOneData) answer).setValue(selection);
				}

				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_MULTI:
				view = LayoutInflater.from(c).inflate(R.layout.widget_select, null);
				answer = new SelectMultiData();

				answerHolder = (LinearLayout) view.findViewById(R.id.widget_selection_holder);
				if(hasInitialValue) {
					Vector<Selection> selections = new Vector<Selection>();
					String[] s = String.valueOf(initialValue).split(" ");
					for(String s_ : s) {
						Selection selection = qd.getChoices().get(Integer.parseInt(s_) - 1).selection();
						selections.add(selection);
					}

					((SelectMultiData) answer).setValue(selections);
				}
				break;
			case org.javarosa.core.model.Constants.CONTROL_AUDIO_CAPTURE:
				view = LayoutInflater.from(c).inflate(R.layout.widget_audio, null);
				
				answerHolder = (ProgressBar) view.findViewById(R.id.widget_play_progress);
				omr = new ODKMediaRecorder(this);
				
				if(hasInitialValue) {
					omr.init(String.valueOf(initialValue));
				} else
					omr.init();
				
				break;
			}

			question_text = (TextView) view.findViewById(R.id.widget_title);
		}

		public ODKView(QuestionDef qd, Activity c) {
			this(qd, c, null);
		}

		@SuppressWarnings("unchecked")
		public void populateAnswer() {
			switch(qd.getControlType()) {
			case org.javarosa.core.model.Constants.CONTROL_INPUT:
				((EditText) answerHolder).setText(answer.getDisplayText());
				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_MULTI:
				for(Selection selection : (Vector<Selection>) answer.getValue()) {
					selectChoices.get(selection.index).cb.setChecked(true);
				}
				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_ONE:
				answerHolder = selectChoices.get(((Selection) answer.getValue()).index).cb;
				((CheckBox) answerHolder).setChecked(true);
				break;
			case org.javarosa.core.model.Constants.CONTROL_AUDIO_CAPTURE:
				break;
			}
		}

		public boolean setAnswer() {
			try {
				if(answerHolder instanceof LinearLayout) {
					Vector<Selection> choices = null;

					for(int c=0; c<((LinearLayout) answerHolder).getChildCount(); c++) {

						if(
								((LinearLayout) answerHolder).getChildAt(c) instanceof CheckBox && 
								((CheckBox) ((LinearLayout) answerHolder).getChildAt(c)).isChecked()
								) {
							// set answer value
							if(choices == null)
								choices = new Vector<Selection>();

							for(SelectChoiceWidget scw : selectChoices) {
								if(((CheckBox) ((LinearLayout) answerHolder).getChildAt(c)).equals(scw.cb)) {								
									choices.add(scw.sc.selection());
									Log.d(LOG, "CHOICE: " + scw.sc.selection().xmlValue);
									break;
								}
							}
						}	
					}

					((SelectMultiData) answer).setValue(choices);

				} else if(answerHolder.getClass().getName().equals(EditText.class.getName())) {
					if(((EditText) answerHolder).getEditableText().toString().length() > 0) {
						((StringData) answer).setValue(((EditText) answerHolder).getEditableText().toString());
						Log.d(LOG, "CHOICE: " + ((StringData) answer).getValue());
					}
				} else if(answerHolder instanceof CheckBox) {

					for(SelectChoiceWidget scw : selectChoices) {
						if(((CheckBox) answerHolder).equals(scw.cb)) {						
							((SelectOneData) answer).setValue(scw.sc.selection());
							Log.d(LOG, "CHOICE: " + scw.sc.selection().xmlValue);
							break;
						}
					}
				} else if(answerHolder instanceof SeekBar) {
					omr.shutDown();
					Log.d(LOG, "CHOICE: " + omr.file.getAbsolutePath());
				}

				return true;
			} catch(ClassCastException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch(NullPointerException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			return false;
		}

		public void setTitle(String questionText) {
			question_text.setText(questionText);
		}

		public void setHelperText(String helperText) {
			TextView helperTextHolder = (TextView) view.findViewById(R.id.widget_summary);
			helperTextHolder.setVisibility(View.VISIBLE);
			helperTextHolder.setText(helperText);
		}

		public void addSelectChoice(SelectChoice sc, String selectChoiceText, Context c) {
			addSelectChoice(sc, selectChoiceText, c, false);
		}

		public void addSelectChoice(SelectChoice sc, String selectChoiceText, Context c, boolean isSingleChoice) {
			if(selectChoices == null)
				selectChoices = new Vector<SelectChoiceWidget>();

			if(choiceHolder == null)
				choiceHolder = (LinearLayout) view.findViewById(R.id.widget_selection_holder);

			SelectChoiceWidget scw = new SelectChoiceWidget(sc, selectChoiceText, c, isSingleChoice);
			choiceHolder.addView(scw.cb);

			selectChoices.add(scw);
		}

		public class SelectChoiceWidget {
			SelectChoice sc;
			CheckBox cb;

			public SelectChoiceWidget(SelectChoice sc, String selectChoiceText, Context c, boolean isSingleChoice) {
				cb = new CheckBox(c);
				cb.setText(selectChoiceText);
				cb.setTextColor(Color.WHITE);

				this.sc = sc;

				if(isSingleChoice) {
					cb.setOnClickListener(new View.OnClickListener() {

						@Override
						public void onClick(View v) {
							if(((CompoundButton) v).isChecked()) {
								for(SelectChoiceWidget scw : selectChoices) {
									if(!scw.cb.equals(v))
										scw.cb.setChecked(false);


								}

								answerHolder = v;
							}

						}
					});
				}
			}
		}
	}
}
