/*
 * Copyright (C) 2010 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.aggregate.format.structure;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.opendatakit.aggregate.CallingContext;
import org.opendatakit.aggregate.constants.HtmlUtil;
import org.opendatakit.aggregate.constants.ServletConsts;
import org.opendatakit.aggregate.constants.format.KmlConsts;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.form.Form;
import org.opendatakit.aggregate.format.RepeatCallbackFormatter;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.format.SubmissionFormatter;
import org.opendatakit.aggregate.format.element.ElementFormatter;
import org.opendatakit.aggregate.format.element.KmlElementFormatter;
import org.opendatakit.aggregate.servlet.BinaryDataServlet;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.aggregate.submission.SubmissionField;
import org.opendatakit.aggregate.submission.SubmissionKey;
import org.opendatakit.aggregate.submission.SubmissionSet;
import org.opendatakit.aggregate.submission.SubmissionValue;
import org.opendatakit.aggregate.submission.type.GeoPoint;
import org.opendatakit.common.constants.BasicConsts;
import org.opendatakit.common.constants.HtmlConsts;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;

/**
 * 
 * @author wbrunette@gmail.com
 * @author adam.lerer@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class KmlFormatter implements SubmissionFormatter, RepeatCallbackFormatter {

  private class GpsRepeatRowData {
    private GeoPoint gps;
    private Row row;
    private String title;
    private String imgUrl;

    public GpsRepeatRowData(GeoPoint gps, String title, String imgUrl, Row row) {
      this.gps = gps;
      this.row = row;
      this.title = title;
      this.imgUrl = imgUrl;
    }

    public GeoPoint getGeoPoint() {
      return gps;
    }

    public Row getRow() {
      return row;
    }

    public String getTitle() {
      return title;
    }

    public String getImgUrl() {
      return imgUrl;
    }
    
  }

  private static final String LIMITATION_MSG = "limitation: image and title must be in the submission (top-level) or must be in the same repeat group as the gps";
  private static final int APPROX_ITEM_LENGTHS = 100;
  private static final int APPROX_TABLE_FORMATTING_LENGTH = 1000;

  private Form form;
  private List<FormElementModel> propertyNames;

  private FormElementModel gpsElement;
  private FormElementModel imgElement;
  private FormElementModel titleElement;
  private FormElementModel topElement;
  private FormElementModel gpsParent;

  private ElementFormatter elemFormatter;

  private String baseWebServerUrl;
  private PrintWriter output;

  private List<GpsRepeatRowData> rowsForGpsInRepeats;

  private boolean imgInGpsRepeat;
  private boolean titleInGpsRepeat;
  
  public KmlFormatter(Form xform, String webServerUrl, FormElementModel gpsField,
      FormElementModel titleField, FormElementModel imgField, PrintWriter printWriter,
      List<FormElementModel> selectedColumnNames, CallingContext cc) {

    form = xform;
    baseWebServerUrl = webServerUrl;
    propertyNames = selectedColumnNames;
    output = printWriter;

    elemFormatter = new KmlElementFormatter(webServerUrl, true, this);

    gpsElement = gpsField;
    titleElement = titleField;
    imgElement = imgField;
    topElement = form.getTopLevelGroupElement();
    gpsParent = gpsField.getParent();

    //verify constraints hold
    FormElementModel titleParent = titleElement.getParent();
    titleInGpsRepeat = (!titleParent.equals(topElement));
    if (!titleParent.equals(topElement) && !titleParent.equals(gpsParent)) {
      throw new IllegalStateException(LIMITATION_MSG);
    }
    if (imgElement == null) {
      imgInGpsRepeat = false;
    } else {
      FormElementModel imgParent = imgElement.getParent();
      imgInGpsRepeat = (!imgParent.equals(topElement));
      if (!imgParent.equals(topElement) && !imgParent.equals(gpsParent)) {
        throw new IllegalStateException(LIMITATION_MSG);
      }
    } 
  }

  @Override
  public void processSubmissions(List<Submission> submissions) throws ODKDatastoreException {
    // output preamble & placemark style
    output.write(String.format(KmlConsts.KML_PREAMBLE_TEMPLATE, form.getFormId(), form
        .getViewableName(), form.getViewableName()));
    output.write(generateStyle(imgElement != null));

    // format row elements
    for (Submission sub : submissions) {


      String id = sub.getKey().getKey();
      StringBuilder placemarks = new StringBuilder();

      // check if gps coordinate is in top element, else it's in a repeat
      if (gpsParent == topElement) {
        // since both gpsParent equals top element, title & imageURL must be in submission
        String title = getTitle(sub);
        String imageURL = getImageUrl(sub);
        
        GeoPoint geopoint = getGeoPoint(sub);
        Row row = sub.getFormattedValuesAsRow(propertyNames, elemFormatter, false);
        placemarks.append(generateFormattedPlacemark(row, StringEscapeUtils.escapeXml(id),
            StringEscapeUtils.escapeXml(title), imageURL, geopoint));
      } else {
        // clear previous rows generated
        rowsForGpsInRepeats = new ArrayList<GpsRepeatRowData>();
       
        // the call back will populate rowsForGpsInRepeats
        sub.getFormattedValuesAsRow(propertyNames, elemFormatter, false);
        for (GpsRepeatRowData repeatData : rowsForGpsInRepeats) {
          String title = titleInGpsRepeat ? repeatData.getTitle() : getTitle(sub);
          String imageURL = imgInGpsRepeat ? repeatData.getImgUrl() : getImageUrl(sub);
          placemarks.append(generateFormattedPlacemark(repeatData.getRow(), StringEscapeUtils
              .escapeXml(id), StringEscapeUtils.escapeXml(title), imageURL, repeatData
              .getGeoPoint()));
        }
      }
      output.write(placemarks.toString());
    }

    // output postamble
    output.write(KmlConsts.KML_POSTAMBLE_TEMPLATE);
  }

  private String generateFormattedPlacemark(Row row, String identifier, String title,
      String imageURL, GeoPoint gp) {

    // make sure no null values slip by
    String id = (identifier == null) ? BasicConsts.EMPTY_STRING : identifier;
    String name = (title == null) ? BasicConsts.EMPTY_STRING : title;

    // determine what data values to create
    String titleStr = (title == null) ? BasicConsts.EMPTY_STRING : generateDataElement(
        KmlConsts.TITLE_VARIABLE, title);
    String imgStr = (imageURL == null) ? BasicConsts.EMPTY_STRING : generateDataElement(
        KmlConsts.IMAGE_VARIABLE, imageURL);

    // create data section
    String dataStr = BasicConsts.EMPTY_STRING;
    if (row.size() > 0) {
      StringBuilder formattedDataStr = new StringBuilder(APPROX_TABLE_FORMATTING_LENGTH
          + row.size() * APPROX_ITEM_LENGTHS);
      formattedDataStr.append(KmlConsts.OPEN_TABLE_W_HEADER_TABLE_FORMAT);
      for (String item : row.getFormattedValues()) {
        formattedDataStr.append(item);
      }
      formattedDataStr.append(HtmlConsts.TABLE_CLOSE);
      dataStr = generateDataElement(KmlConsts.DATA_VARIABLE, formattedDataStr.toString());
    }

    // Create Geopoint
    String geopoint = BasicConsts.EMPTY_STRING;
    if (gp != null) {
      if (gp.getLatitude() != null && gp.getLongitude() != null) {
        // TODO: why are we using BigDecimal
        BigDecimal altitude = new BigDecimal(0.0);
        if (gp.getAltitude() != null) {
          altitude = gp.getAltitude();
        }
        geopoint = String.format(KmlConsts.KML_PLACEMARK_POINT_TEMPLATE, gp.getLongitude()
            + BasicConsts.COMMA + gp.getLatitude() + BasicConsts.COMMA + altitude);
      }
    }

    return String.format(KmlConsts.KML_PLACEMARK_TEMPLATE, id, name, titleStr, imgStr, dataStr,
        geopoint);
  }

  public void processRepeatedSubmssionSetsIntoRow(List<SubmissionSet> repeats,
      FormElementModel repeatElement, Row row) throws ODKDatastoreException {
    
    for (SubmissionSet repeatSet : repeats) {
      Row rowFromRepeat = repeatSet.getFormattedValuesAsRow(propertyNames, elemFormatter, false);
      if (repeatElement.equals(gpsParent)) {
        Row clonedRow = Row.cloneRowValues(row);
        clonedRow.addDataFromRow(rowFromRepeat);
        GeoPoint geopoint = getGeoPoint(repeatSet);
        String title = titleInGpsRepeat ?  getTitle(repeatSet) : null;
        String imageURL = imgInGpsRepeat ?  getImageUrl(repeatSet) : null;
        GpsRepeatRowData repeatData = new GpsRepeatRowData(geopoint, title, imageURL, clonedRow);
        rowsForGpsInRepeats.add(repeatData);
      } else {
        row.addDataFromRow(rowFromRepeat);
      }
    }
  }
  
  private String generateStyle(boolean hasImage) {
    String styleHtml = KmlConsts.OPEN_TABLE_W_PARENT_TABLE_FORMAT;
    styleHtml += wrapInBothRowNData(HtmlUtil.wrapWithHtmlTags(HtmlConsts.H2,
        wrapVariable(KmlConsts.TITLE_VARIABLE)));
    if (hasImage) {
      styleHtml += HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW, KmlConsts.IMAGE_FORMAT);
    }
    styleHtml += wrapInBothRowNData(wrapVariable(KmlConsts.DATA_VARIABLE));
    styleHtml += HtmlConsts.TABLE_CLOSE;

    return String.format(KmlConsts.KML_STYLE_TEMPLATE, KmlConsts.PLACEMARK_STYLE, styleHtml);
  }

  private String generateDataElement(String name, String value) {
    return String.format(KmlConsts.KML_DATA_ELEMENT_TEMPLATE, StringEscapeUtils.escapeXml(name),
        StringEscapeUtils.escapeXml(value));
  }

  private String wrapVariable(String variable) {
    return KmlConsts.VARIABLE_BEGIN + variable + BasicConsts.RIGHT_BRACKET;
  }

  private String wrapInBothRowNData(String value) {
    return HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW, HtmlUtil.wrapWithHtmlTags(
        HtmlConsts.TABLE_DATA, value));
  }

  private Map<String, String> createViewLinkProperties(SubmissionKey subKey) {
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(ServletConsts.BLOB_KEY, subKey.toString());
    return properties;
  }

  private GeoPoint getGeoPoint(SubmissionSet set) {
    SubmissionValue geopointField = set.getElementValue(gpsElement);
    if (geopointField != null) {
      Object value = ((SubmissionField<?>) geopointField).getValue();
      if (value != null && value instanceof GeoPoint) {
        return (GeoPoint) value;
      }
    }
    return null;
  }

  private String getTitle(SubmissionSet set) {
    // TODO: why are we not dealing in SubmissionField?
    SubmissionValue titleField = set.getElementValue(titleElement);
    if (titleField != null) {
      Object titleValue = ((SubmissionField<?>) titleField).getValue();
      if (titleValue != null) {
        return titleValue.toString();
      }
    }
    return null;
  }

  private String getImageUrl(SubmissionSet set) {
    // TODO: why are we not dealing in SubmissionField?
    SubmissionValue imageField = set.getElementValue(imgElement);
    if (imageField != null) {
      Object value = ((SubmissionField<?>) imageField).getValue();
      if (value != null && value instanceof SubmissionKey) {
        SubmissionKey key = (SubmissionKey) value;
        Map<String, String> properties = createViewLinkProperties(key);
        String url = HtmlUtil.createHttpServletLink(baseWebServerUrl, BinaryDataServlet.ADDR);
        return HtmlUtil.createLinkWithProperties(url, properties);
      }
    }
    return null;
  }
}