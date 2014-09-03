/**
 * This file is part of pwt.
 *
 * pwt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * pwt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with pwt.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.putnami.pwt.core.widget.client;

import com.google.common.base.Strings;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.DataTransfer;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragLeaveHandler;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragOverHandler;
import com.google.gwt.event.dom.client.DropEvent;
import com.google.gwt.event.dom.client.DropHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RootPanel;

import fr.putnami.pwt.core.editor.client.EditorInput;
import fr.putnami.pwt.core.event.client.HandlerRegistrationCollection;
import fr.putnami.pwt.core.model.client.base.HasDrawable;
import fr.putnami.pwt.core.theme.client.CssStyle;
import fr.putnami.pwt.core.theme.client.IconFont;
import fr.putnami.pwt.core.widget.client.Button.Type;
import fr.putnami.pwt.core.widget.client.base.AbstractInput;
import fr.putnami.pwt.core.widget.client.base.SimpleStyle;
import fr.putnami.pwt.core.widget.client.constant.WidgetParams;
import fr.putnami.pwt.core.widget.client.event.ButtonEvent;
import fr.putnami.pwt.core.widget.client.util.StyleUtils;
import fr.putnami.pwt.core.widget.client.util.UUID;
import fr.putnami.pwt.core.widget.shared.domain.FileDto;
import fr.putnami.pwt.core.widget.shared.domain.UploadStatus;

public class InputFile extends InputGroup<FileDto>
implements HasDrawable, EditorInput<FileDto> {

	private static final String MULTIPART_BOUNDARY = "x-x-x-x-x";
	private static final String EOL = "\r\n";

	private static final String URL_UPLOAD = GWT.getHostPageBaseURL() + "file/upload/";
	private static final String URL_STATUS = GWT.getHostPageBaseURL() + "file/status/";
	private static final String URL_DOWNLOAD = GWT.getHostPageBaseURL() + "file/download/";

	private static final CssStyle STYLE_DRAGOVER = new SimpleStyle("file-dragover");
	private static final CssStyle STYLE_MUTTED = new SimpleStyle("text-muted");

	final Timer timer = new Timer() {
		@Override
		public void run() {
			statusSendRequest();
		}
	};

	private class UploadForm {

		private final FormPanel uploadForm = new FormPanel();
		private final FileUpload fileUpload = new FileUpload();

		private final HandlerRegistrationCollection handlerRegistrations = new HandlerRegistrationCollection();

		public UploadForm() {
			uploadForm.setMethod("post");
			uploadForm.setEncoding("multipart/form-data");
			uploadForm.getElement().getStyle().setHeight(0, Unit.PX);
			uploadForm.getElement().getStyle().setWidth(0, Unit.PX);
			uploadForm.getElement().getStyle().setOverflow(Overflow.HIDDEN);
			uploadForm.add(fileUpload);

			fileUpload.setName("data");

			handlerRegistrations.add(fileUpload.addChangeHandler(new ChangeHandler() {
				@Override
				public void onChange(ChangeEvent event) {
					handlerRegistrations.add(uploadForm.addSubmitCompleteHandler(new SubmitCompleteHandler() {

						@Override
						public void onSubmitComplete(SubmitCompleteEvent event) {
							// Hook to strip <pre> on some brothers
							Element label = DOM.createLabel();
							label.setInnerHTML(event.getResults());
							handleCompleteJson(label.getInnerText());
						}
					}));

					fileId = UUID.uuid();
					uploadForm.setAction(URL_UPLOAD + fileId);
					uploadForm.submit();
					int fileSize = getFileSize(fileUpload.getElement());
					initProgressBar(fileSize);
				}
			}));
		}

		public UploadForm destroy() {
			InputElement.as(fileUpload.getElement()).setValue(null);
			handlerRegistrations.removeHandler();
			uploadForm.removeFromParent();

			return null;
		}

		public void openFilePicker() {
			RootPanel.get().add(uploadForm);
			nativeClick(fileUpload.getElement());
		}
	}

	private final WidgetParams params = WidgetParams.Util.get();

	private final OutputProgressBar<Integer> progressBar = new OutputProgressBar<Integer>();
	private final OneWidgetPanel progressBarWrapper = new OneWidgetPanel();
	private final Anchor<?> fileNameAnchor = new Anchor();
	private final Text placeholderText = new Text();

	private final Button<?> cancelBtn = new Button();
	private final Button<?> uploadBtn = new Button();

	private String placeholder = null;

	private String fileId;
	private UploadForm uploadForm;
	private Request sendRequest;

	public InputFile() {
		endConstruct();
	}

	protected InputFile(InputFile source) {
		super(source, false);
		endConstruct();
	}

	private void endConstruct() {

		progressBar.setDisplayValue(true);
		progressBar.setAnimated(true);
		progressBarWrapper.add(progressBar);

		StyleUtils.addStyle(progressBarWrapper, AbstractInput.STYLE_CONTROL);
		StyleUtils.addStyle(fileNameAnchor, AbstractInput.STYLE_CONTROL);
		StyleUtils.addStyle(placeholderText, AbstractInput.STYLE_CONTROL);
		StyleUtils.addStyle(placeholderText, STYLE_MUTTED);

		cancelBtn.setType(Type.ICON);
		cancelBtn.setIconType(IconFont.ICON_CANCEL);
		cancelBtn.addButtonHandler(new ButtonEvent.Handler() {
			@Override
			public void onButtonAction(ButtonEvent event) {
				if (sendRequest != null) {
					sendRequest.cancel();
				}
				edit(null);
			}
		});
		uploadBtn.setType(Type.ICON);
		uploadBtn.setIconType(IconFont.ICON_UPLOAD);
		uploadBtn.addButtonHandler(new ButtonEvent.Handler() {
			@Override
			public void onButtonAction(ButtonEvent event) {
				if (uploadForm == null) {
					uploadForm = new UploadForm();
				}
				uploadForm.openFilePicker();
			}
		});

		this.addDomHandler(new DragLeaveHandler() {
			@Override
			public void onDragLeave(DragLeaveEvent event) {
				StyleUtils.removeStyle(InputFile.this, STYLE_DRAGOVER);
			}
		}, DragLeaveEvent.getType());
		this.addDomHandler(new DragOverHandler() {
			@Override
			public void onDragOver(DragOverEvent event) {
				StyleUtils.addStyle(InputFile.this, STYLE_DRAGOVER);
			}
		}, DragOverEvent.getType());
		this.addDomHandler(new DropHandler() {
			@Override
			public void onDrop(DropEvent event) {
				event.preventDefault();
				event.stopPropagation();

				DataTransfer data = event.getNativeEvent().getDataTransfer();
				nativeUploadData(data, InputFile.this);
			}
		}, DropEvent.getType());

		redraw();
	}

	@Override
	public IsWidget cloneWidget() {
		return new InputFile(this);
	}

	@Override
	public void redraw() {
		cancelBtn.removeFromParent();
		uploadBtn.removeFromParent();
		fileNameAnchor.removeFromParent();
		placeholderText.removeFromParent();
		progressBarWrapper.removeFromParent();
		if (fileId != null) {
			append(progressBarWrapper);
			addAddon(cancelBtn);
		}
		else {
			FileDto value = getValue();
			if (value != null) {
				NumberFormat nf = NumberFormat.getFormat("#.##");

				long size = value.getContentLength();
				String displaySize = "";
				if (size > 1024 * 1024) {
					displaySize = nf.format(size / (1024 * 1024D)) + " MB";
				}
				else if (size > 1024) {
					displaySize = nf.format(size / 1024D) + " KB";
				}
				else {
					displaySize = nf.format(size) + " B";
				}

				fileNameAnchor.setLink(URL_DOWNLOAD + value.getToken());
				fileNameAnchor.setText(value.getName() + " - (" + displaySize + ")");
				placeholderText.setText(null);
				append(fileNameAnchor);
				addAddon(cancelBtn);
			}
			else {
				fileNameAnchor.setLink(null);
				fileNameAnchor.setText(null);
				placeholderText.setText(placeholder);
				append(placeholderText);
			}
			addAddon(uploadBtn);
		}
	}

	public String getPlaceholder() {
		return placeholder;
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
		placeholderText.setText(placeholder);
	}

	@Override
	public void edit(FileDto object) {
		FileDto oldValue = this.getValue();
		super.edit(object);
		timer.cancel();
		redraw();
	}

	private void initProgressBar(int size) {
		progressBar.edit(0);
		redraw();
		if (params.inputFileProgressEnable()) {
			progressBar.setMax(size);
			progressBar.edit(0);
			timer.schedule(10);
		}
		else {
			progressBar.setDisplayValue(false);
			progressBar.setMax(100);
			progressBar.edit(40);
		}
	}

	private void statusSendRequest() {
		if (fileId == null) {
			return;
		}

		RequestCallback callback = new RequestCallback() {
			@Override
			public void onResponseReceived(Request request, Response response) {
				if (200 == response.getStatusCode()) {
					handleStatusJson(response.getText());
				}
			}

			@Override
			public void onError(Request request, Throwable exception) {
			}
		};

		try {
			RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, URL_STATUS + fileId);
			requestBuilder.setHeader("Cache-Control", "max-age=0");
			requestBuilder.sendRequest("", callback);
		}
		catch (RequestException e) {
			throw new RuntimeException("Couldn't send request", e);
		}
	}

	private void uploadSendRequest(String base64data, String fileName, String type, int size) {
		RequestCallback callback = new RequestCallback() {
			@Override
			public void onResponseReceived(Request request, Response response) {
				if (200 == response.getStatusCode()) {
					handleCompleteJson(response.getText());
				}
				else {
					displayError("Couldn't retrieve JSON (" + response.getStatusText() + ")");
				}
			}

			@Override
			public void onError(Request request, Throwable exception) {
				displayError("Couldn't retrieve JSON");
			}

		};

		fileId = UUID.uuid();
		initProgressBar(size);

		StringBuffer requestBody = new StringBuffer();
		requestBody.append("--").append(MULTIPART_BOUNDARY).append(EOL)
		.append("Content-Disposition: form-data; name=\"data\"; filename=\"").append(fileName).append("\"").append(EOL)
		.append("Content-Type: ").append(type).append(EOL).append(EOL)
		.append(base64data).append(EOL)
		.append("--").append(MULTIPART_BOUNDARY).append("--");

		try {
			RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.POST, URL_UPLOAD + fileId);
			requestBuilder.setHeader("content-type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY);
			requestBuilder.setHeader("Cache-Control", "max-age=0");
			this.sendRequest = requestBuilder.sendRequest(requestBody.toString(), callback);

		}
		catch (RequestException e) {
			throw new RuntimeException("Couldn't send request", e);
		}

	}

	private void handleStatusJson(String reponseData) {

		if (!Strings.isNullOrEmpty(reponseData)) {
			JSONObject jsObject = JSONParser.parseLenient(reponseData).isObject();
			UploadStatus status = new UploadStatus();
			status.setBytesRead((long) jsObject.get("bytesRead").isNumber().doubleValue());
			status.setContentLength((long) jsObject.get("contentLength").isNumber().doubleValue());
			status.setUploadId(jsObject.get("uploadId").isString().stringValue());

			progressBar.setMax(Long.valueOf(status.getContentLength()).intValue());
			progressBar.edit((int) status.getBytesRead());
		}
		else {
			progressBar.setValue(progressBar.getMax());
		}
		if (fileId != null) {
			timer.schedule(100);
		}

	}

	private void handleCompleteJson(String reponseData) {
		JSONObject jsObject = JSONParser.parseLenient(reponseData).isObject();

		final FileDto file = new FileDto();
		file.setName(jsObject.get("name").isString().stringValue());
		file.setExtension(jsObject.get("extension").isString().stringValue());
		file.setMime(jsObject.get("mime").isString().stringValue());
		file.setToken(jsObject.get("token").isString().stringValue());
		file.setContentLength((long) jsObject.get("contentLength").isNumber().doubleValue());

		if (uploadForm != null) {
			uploadForm = uploadForm.destroy();
		}
		sendRequest = null;
		fileId = null;

		progressBar.edit(progressBar.getMax());

		Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {

			@Override
			public boolean execute() {
				edit(file);
				return false;
			}
		}, params.inputFileProgressHideDelay());
	}

	private void displayError(String string) {

	}

	private static native void nativeUploadData(DataTransfer dataTransfer, InputFile inputFile)
	/*-{
		var files = dataTransfer.files;
		for (var i = 0; i < files.length; i++ ) {
			var file = files[i];
			var b64reader = new FileReader();
			b64reader.onloadend = (function(file) {
					return function(base64) {
						file.base64data = base64.target.result;
						inputFile.@fr.putnami.pwt.core.widget.client.InputFile::uploadSendRequest(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)
							(file.base64data, file.name, file.type, file.size);
					};
				})(file);
			b64reader.readAsBinaryString(file);
	   }
	}-*/;

	private static native int getFileSize(Element fileElement)
	/*-{
		var files = fileElement.files;
		for (var i = 0; i < files.length; i++ ) {
			return files[i].size;
		}
		return 0;
	}-*/;

	private static native void nativeClick(Element elem)
	/*-{
		elem.click();
	}-*/;

}
