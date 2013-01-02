package info.guardianproject.odkparser.ui;

import info.guardianproject.odkparser.Constants;
import info.guardianproject.odkparser.FormWrapper;
import info.guardianproject.odkparser.R;
import info.guardianproject.odkparser.FormWrapper.UIBinder;
import info.guardianproject.odkparser.ui.FormWidgetFactory.ODKView;
import info.guardianproject.odkparser.ui.FormWidgetFactory.WidgetDataController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Vector;

import org.javarosa.form.api.FormEntryController;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FormHolder extends FragmentActivity implements Constants, WidgetDataController, OnClickListener, ViewPager.OnPageChangeListener, UIBinder {
	private ODKAdapter odk_adapter;
	private ViewPager frame_holder_pager;

	Button form_save;
	LinearLayout progress_holder;
	int d, d_, num_bars;
	
	int export_mode = Form.ExportMode.XML_URI;
	int max_questions_per_page = Form.MAX_QUESTIONS_PER_PAGE;

	ByteArrayInputStream form_def_bytes = null;
	
	FormWrapper fw = null;
	File dump;

	private static final String LOG = Logger.UI;
	boolean hasSeenLastPage = false;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.form_holder_activity);
		try {
			Uri form_uri = getIntent().getData();
			InputStream is = getContentResolver().openInputStream(form_uri);
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			is.close();
			
			form_def_bytes = new ByteArrayInputStream(bytes);
			Log.d(Logger.FORM, "form uri: " + form_uri.toString());

		} catch(NullPointerException e) {
			Log.e(Logger.FORM, "trying to inflate from byte array...");
			if(getIntent().hasExtra(Form.Extras.DEF_PATH))
				form_def_bytes = new ByteArrayInputStream(getIntent().getByteArrayExtra(Form.Extras.DEF_PATH));
			else 
				finish();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(getIntent().hasExtra(Form.Extras.DATA_DUMP))
			dump = new File(getIntent().getStringExtra(Form.Extras.DATA_DUMP));
		else
			dump = new File(Environment.getExternalStorageDirectory(), "ODKFormUI");
		
		if(!dump.exists())
			dump.mkdirs();
		
		if(getIntent().hasExtra(Form.Extras.EXPORT_MODE))
			export_mode = getIntent().getIntExtra(Form.Extras.EXPORT_MODE, Form.ExportMode.XML_URI);
		
		if(getIntent().hasExtra(Form.Extras.MAX_QUESTIONS_PER_PAGE))
			max_questions_per_page = getIntent().getIntExtra(Form.Extras.MAX_QUESTIONS_PER_PAGE, Form.MAX_QUESTIONS_PER_PAGE);

		form_save = (Button) findViewById(R.id.form_save);
		form_save.setOnClickListener(this);

		if(getIntent().hasExtra(Form.Extras.PREVIOUS_ANSWERS)) {
			Log.d(LOG, "HAS EXTRAS FOR PREVIOUS ANSWERS");
			fw = new FormWrapper(form_def_bytes, getIntent().getByteArrayExtra(Form.Extras.PREVIOUS_ANSWERS), this);
		} else
			fw = new FormWrapper(form_def_bytes, this);

		if(fw == null)
			finish();
		
		if(getIntent().hasExtra(Form.Extras.DEFAULT_TITLE))
			setMainTitle(getIntent().getStringExtra(Form.Extras.DEFAULT_TITLE));
		else
			setMainTitle(fw.title);
		
		if(getIntent().hasExtra(Form.Extras.DEFAULT_THUMB))
			setMainIcon(getIntent().getByteArrayExtra(Form.Extras.DEFAULT_THUMB));
		
		

		initFormFragments();
	}

	@Override
	public void setMainTitle(String title) {
		TextView tv = (TextView) findViewById(R.id.formNameHolder);
		tv.setText(title);
	}
	
	public void setMainIcon(byte[] bitmap_bytes) {
		setMainIcon(BitmapFactory.decodeByteArray(bitmap_bytes, 0, bitmap_bytes.length));
	}
	
	public void setMainIcon(Bitmap bitmap) {
		ImageView iv = (ImageView) findViewById(R.id.imageRegionThumb);
		iv.setImageBitmap(bitmap);
	}
	
	private void initFormFragments() {
		// 3 questions per fragment by default...
		List<Fragment> fragments = new Vector<Fragment>();

		for(int f=0; f<fw.num_questions; f++) {
			if(f % max_questions_per_page == 0) {
				Bundle args = new Bundle();
				args.putInt(Form.Keys.BIND_ID, f);

				Fragment fragment = Fragment.instantiate(this, FormPage.class.getName());
				fragment.setArguments(args);
				fragments.add(fragment);
			}
		}

		odk_adapter = new ODKAdapter(getSupportFragmentManager(), fragments);
		frame_holder_pager = (ViewPager) findViewById(R.id.frame_holder_pager);
		frame_holder_pager.setAdapter(odk_adapter);
		frame_holder_pager.setOnPageChangeListener(this);

		progress_holder = (LinearLayout) findViewById(R.id.form_progress_holder);

		d = R.drawable.odkform_bullet_inactive;
		d_ = R.drawable.odkform_bullet_active;
		
		num_bars = (fw.num_questions/max_questions_per_page);
		if(fw.num_questions % max_questions_per_page > 0)
			num_bars++;
		
		redrawProgressView();
	}
	
	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		redrawProgressView();
	}
	
	private void redrawProgressView() {
		@SuppressWarnings("deprecation")
		int windowWidth = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getWidth();
		LayoutParams lp = new LinearLayout.LayoutParams(windowWidth / (int) num_bars, LayoutParams.WRAP_CONTENT);

		progress_holder.removeAllViews();
		
		for(int p=0; p<odk_adapter.getCount(); p++) {
			ImageView progress_view = new ImageView(this);
			progress_view.setLayoutParams(lp);
			progress_view.setBackgroundResource(p == 0 ? d_ : d);
			
			try {
				((ViewGroup) progress_view.getParent()).removeView(progress_view);
			} catch(NullPointerException e) {}
			
			progress_holder.addView(progress_view);
		}
	}

	public static class ODKAdapter extends FragmentStatePagerAdapter {
		List<Fragment> fragments;
		FragmentManager fm;

		public ODKAdapter(FragmentManager fm, List<Fragment> fragments) {
			super(fm);
			this.fm = fm;
			this.fragments = fragments;


		}

		@Override
		public Fragment getItem(int position) {
			return fragments.get(position);
		}

		@Override
		public int getCount() {
			return fragments.size();
		}

	}

	@Override
	public void onPageScrollStateChanged(int state) {}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {}

	@Override
	public void onPageSelected(int page) {
		for(int v=0; v<progress_holder.getChildCount(); v++) {
			View view = progress_holder.getChildAt(v);
			if(view instanceof ImageView)
				((ImageView) view).setBackgroundResource(page == v ? d_ : d);
		}

		if(page == progress_holder.getChildCount() - 1 && !hasSeenLastPage)
			hasSeenLastPage = true;
	}

	@Override
	public List<ODKView> getQuestionsForDisplay(int first, int last) {
		if(last <= fw.questions.size())
			return fw.questions.subList(first, last);
		else
			return fw.questions.subList(first, fw.questions.size());

	}

	@Override
	public FormEntryController controller() {
		return fw.controller;
	}

	@Override
	public boolean answerQuestion(ODKView odkView) {
		return fw.answerQuestion(odkView.qd, odkView.answer);
	}
	
	private void alertError(int str) {
		Toast.makeText(this, getString(str), Toast.LENGTH_LONG).show();
	}

	public void saveForm() {
		Intent intent = new Intent();
		intent.putExtras(getIntent().getExtras());
		
		switch(export_mode) {
		case Form.ExportMode.XML_BAOS:
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OutputStream os_ = fw.processFormAsXML(baos);
				os_.flush();
				os_.close();
				
				intent.putExtra(Form.Extras.PREVIOUS_ANSWERS, baos.toByteArray());
			} catch (IOException e) {
				Log.e(Logger.FORM, e.toString());
				e.printStackTrace();
			}
			
			
			
			break;
		case Form.ExportMode.JSON:
			JSONObject json =  fw.processFormAsJSON();
			if(json != null)
				intent.putExtra(Form.Extras.JSON_FORM, json.toString().getBytes());
			else
				alertError(R.string.error_save_fail);
			
			break;
		case Form.ExportMode.XML_URI:
			File testDir = new File(Environment.getExternalStorageDirectory(), "odktest");
			if(!testDir.exists())
				testDir.mkdir();
			
			File testFile = new File(testDir, "odkanswers_" + System.currentTimeMillis() + ".xml");
			try {
				OutputStream os = fw.processFormAsXML(new FileOutputStream(testFile));
				os.flush();
				os.close();
				intent.setData(Uri.fromFile(testFile));
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				alertError(R.string.error_save_fail);
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				alertError(R.string.error_save_fail);
			}
			
			break;
		}
		
		setResult(Activity.RESULT_OK, intent);
		finish();
	}
	
	@Override
	public void onClick(View v) {
		if(v == form_save) {
			if(hasSeenLastPage) {
				for(Fragment fragment : odk_adapter.fragments)
					((FormPage) fragment).getAnswers();

				saveForm();
			} else
				Toast.makeText(this, getString(R.string.error_not_finished), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public int getMaxQuestionsPerPage() {
		return max_questions_per_page;
	}
	
	@Override
	public File openRecorderStream() {
		return new File(dump, System.currentTimeMillis() + "_rec.3gp");
	}

	@Override
	public void onSave() {
		// TODO Auto-generated method stub
		
	}

}
