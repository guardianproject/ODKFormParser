package info.guardianproject.odkparser.utils;

import info.guardianproject.odkparser.FormWrapper;

import java.util.List;
import java.util.Vector;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.data.helper.Selection;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.RadioButton;;

public class QD extends Model {
	QuestionDef questionDef;
	IAnswerData answer;
	View answerHolder;
	byte[] attachment = null;
	
	public String id = null;
	public String initialValue = null;
	public String questionText = null;
	public String helperText = null;
	public boolean hasInitialValue = false;
	
	public void setQuestionDef(QuestionDef questionDef) {
		this.questionDef = questionDef;
		this.id = this.questionDef.getTextID();
	}
	
	public QD(QuestionDef questionDef) {
		setQuestionDef(questionDef);
	}
	
	public QD(View answerHolder) {
		this(null, answerHolder);
	}
	
	public QD(String initialValue, View answerHolder) {
		this.initialValue = initialValue;
		this.hasInitialValue = true;
		this.answerHolder = answerHolder;
	}
	
	public void answer() {
		switch(questionDef.getControlType()) {
		case org.javarosa.core.model.Constants.CONTROL_INPUT:
			if(((EditText) answerHolder).getText().length() > 0) {
				((StringData) answer).setValue(((EditText) answerHolder).getText().toString());
				Log.d(LOG, "setting answer " + ((StringData) answer).getValue());
			}
		
			break;
		case org.javarosa.core.model.Constants.CONTROL_SELECT_ONE:
			for(int o=0; o < ((ViewGroup) answerHolder).getChildCount(); o++) {
				if(
						((ViewGroup) answerHolder).getChildAt(o) instanceof RadioButton &&
						((RadioButton) ((ViewGroup) answerHolder).getChildAt(o)).isChecked()
				) {
					((SelectOneData) answer).setValue(questionDef.getChoices().get(o).selection());
					break;
				}
			}
			break;
		case org.javarosa.core.model.Constants.CONTROL_SELECT_MULTI:
			List<Selection> choices = null;
			int choiceIndex = 0;
			for(int m=0; m< ((ViewGroup) answerHolder).getChildCount(); m++) {
				if(
						((ViewGroup) answerHolder).getChildAt(m) instanceof CheckBox &&
						((CheckBox) ((ViewGroup) answerHolder).getChildAt(m)).isChecked()
				) {
					if(choices == null) {
						choices = new Vector<Selection>();
					}
					
					choices.add(questionDef.getChoices().get(choiceIndex).selection());
					
				}
				
				choiceIndex++;
			}
			
			((SelectMultiData) answer).setValue(choices);
			break;
		case org.javarosa.core.model.Constants.CONTROL_AUDIO_CAPTURE:
			break;
		}
	}
	
	public void commit(FormWrapper fw) {
		if(fw.answerQuestion(questionDef, answer)) {
			initialValue = String.valueOf(answer.getValue());
		}
		
	}
	
	public void pin(String initialValue, View answerHolder) {
		this.answerHolder = answerHolder;
		
		Log.d(LOG, "pinning to " + answerHolder.getClass().getName());
		switch(questionDef.getControlType()) {
		case org.javarosa.core.model.Constants.CONTROL_INPUT:
			if(initialValue != null) {
				answer = new StringData(String.valueOf(initialValue));
				((EditText) this.answerHolder).setText(initialValue);
			} else {
				answer = new StringData("");
				((EditText) this.answerHolder).setHint(questionDef.getHelpText());
			}
			
			break;
		case org.javarosa.core.model.Constants.CONTROL_SELECT_ONE:
			answer = new SelectOneData();
			
			if(initialValue != null) {
				Selection selection = questionDef.getChoices().get(Integer.parseInt(initialValue) - 1).selection();
				((SelectOneData) answer).setValue(selection);
				
				RadioButton rb = (RadioButton) ((ViewGroup) this.answerHolder).getChildAt(Integer.parseInt(initialValue) - 1);
				rb.setChecked(true);				
			}
			
			break;
		case org.javarosa.core.model.Constants.CONTROL_SELECT_MULTI:
			answer = new SelectMultiData();
			
			if(initialValue != null) {
				Vector<Selection> selections = new Vector<Selection>();
				String[] selectionsString = String.valueOf(initialValue).split(" ");
				for(String s : selectionsString) {
					Selection selection = questionDef.getChoices().get(Integer.parseInt(s) - 1).selection();
					selections.add(selection);
					
					((CheckBox) ((ViewGroup) this.answerHolder).getChildAt(Integer.parseInt(s) - 1)).setChecked(true);
				}
				
				((SelectMultiData) answer).setValue(selections);
			}
			break;
		case org.javarosa.core.model.Constants.CONTROL_AUDIO_CAPTURE:
			answer = new UncastData("");
			
			
			if(initialValue != null) {
				answer = new UncastData(initialValue);
				((UncastData) answer).setValue(initialValue);
			}
			
			break;
		}
	}
	
	
}
