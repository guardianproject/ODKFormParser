package info.guardianproject.odkparser.ui;

import java.util.List;
import java.util.Vector;

import info.guardianproject.odkparser.Constants;
import info.guardianproject.odkparser.R;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.helper.Selection;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FormWidgetFactory {

	static final String LOG = Constants.Logger.WIDGET_FACTORY;

	public interface WidgetDataController {
		public void onSave();
	}

	public static class ODKView {
		View view, answerHolder;
		QuestionDef qd;
		IAnswerData answer;

		TextView question_text;
		LinearLayout choiceHolder = null;

		List<SelectChoiceWidget> selectChoices = null;
		boolean hasInitialValue = false;

		public ODKView(QuestionDef qd, Context c, String initialValue) {
			this.qd = qd;
			if(initialValue != null)
				hasInitialValue = true;

			switch(qd.getControlType()) {
			case org.javarosa.core.model.Constants.CONTROL_INPUT:
				view = LayoutInflater.from(c).inflate(R.layout.widget_textinput, null);
				if(!hasInitialValue)
					answer = new StringData();
				else
					answer = new StringData(String.valueOf(initialValue));
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
			}

			question_text = (TextView) view.findViewById(R.id.widget_title);
		}

		public ODKView(QuestionDef qd, Context c) {
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
									break;
								}
							}
						}	
					}

					((SelectMultiData) answer).setValue(choices);

				} else if(answerHolder.getClass().getName().equals(EditText.class.getName())) {
					if(((EditText) answerHolder).getEditableText().toString().length() > 0)
						((StringData) answer).setValue(((EditText) answerHolder).getEditableText().toString());
				} else if(answerHolder instanceof CheckBox) {

					for(SelectChoiceWidget scw : selectChoices) {
						if(((CheckBox) answerHolder).equals(scw.cb)) {						
							((SelectOneData) answer).setValue(scw.sc.selection());
							break;
						}
					}
				}

				return true;
			} catch(ClassCastException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch(NullPointerException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();

				Log.e(LOG, "this answer is null");

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
