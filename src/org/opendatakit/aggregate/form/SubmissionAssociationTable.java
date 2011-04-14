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
package org.opendatakit.aggregate.form;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.aggregate.CallingContext;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.PersistConsts;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.DataField.IndexType;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.security.User;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public final class SubmissionAssociationTable extends CommonFieldsBase {
	private static final String TABLE_NAME = "_form_info_submission_association";

	private static final DataField URI_MD5_SUBMISSION_FORM_ID = new DataField("URI_MD5_SUBMISSION_FORM_ID",
			DataField.DataType.URI, false, PersistConsts.URI_STRING_LEN).setIndexable(IndexType.HASH);
	
	private static final DataField URI_MD5_FORM_ID = new DataField("URI_MD5_FORM_ID",
			DataField.DataType.URI, false, PersistConsts.URI_STRING_LEN).setIndexable(IndexType.HASH);
	
	private static final DataField SUBMISSION_FORM_ID = new DataField("SUBMISSION_FORM_ID",
			DataField.DataType.STRING, true, PersistConsts.MAX_SIMPLE_STRING_LEN);

	private static final DataField SUBMISSION_MODEL_VERSION = new DataField("SUBMISSION_MODEL_VERSION",
			DataField.DataType.INTEGER, true);

	private static final DataField SUBMISSION_UI_VERSION = new DataField("SUBMISSION_UI_VERSION",
			DataField.DataType.INTEGER, true);

	private static final DataField IS_PERSISTENCE_MODEL_COMPLETE = new DataField("IS_PERSISTENCE_MODEL_COMPLETE",
			DataField.DataType.BOOLEAN, true);

	private static final DataField IS_SUBMISSION_ALLOWED = new DataField("IS_SUBMISSION_ALLOWED",
			DataField.DataType.BOOLEAN, true);

	private static final DataField URI_SUBMISSION_DATA_MODEL = new DataField("URI_SUBMISSION_DATA_MODEL",
			DataField.DataType.URI, true);

	/**
	 * DOM_AURI (md5 of submission form_id)
	 * SUB_AURI (URI_FORM_INFO)
	 */
	/**
	 * Construct a relation prototype. Only called via {@link #assertRelation(CallingContext)}
	 * 
	 * @param databaseSchema
	 */
	private SubmissionAssociationTable(String databaseSchema) {
		super(databaseSchema, TABLE_NAME);

		fieldList.add(URI_MD5_SUBMISSION_FORM_ID);
		fieldList.add(URI_MD5_FORM_ID);
		fieldList.add(SUBMISSION_FORM_ID);
		fieldList.add(SUBMISSION_MODEL_VERSION);
		fieldList.add(SUBMISSION_UI_VERSION);
		fieldList.add(IS_PERSISTENCE_MODEL_COMPLETE);
		fieldList.add(IS_SUBMISSION_ALLOWED);
		fieldList.add(URI_SUBMISSION_DATA_MODEL);
	}

	/**
	 * Construct an empty entity. Only called via {@link #getEmptyRow(User)}
	 * 
	 * @param ref
	 * @param user
	 */
	private SubmissionAssociationTable(SubmissionAssociationTable ref, User user) {
		super(ref, user);
	}

	// Only called from within the persistence layer.
	@Override
	public SubmissionAssociationTable getEmptyRow(User user) {
		return new SubmissionAssociationTable(this, user);
	}
	
	public XFormParameters getXFormParameters() {
		return new XFormParameters( getSubmissionFormId(), 
				getSubmissionModelVersion(), getSubmissionUiVersion());
	}
	
	public String getUriMd5FormId() {
		return getStringField(URI_MD5_FORM_ID);
	}
	
	public void setUriMd5FormId(String value) {
		if ( !setStringField(URI_MD5_FORM_ID, value) ) {
			throw new IllegalStateException("overflow uriMd5FormId");
		}
	}
	
	public String getUriMd5SubmissionFormId() {
		return getStringField(URI_MD5_SUBMISSION_FORM_ID);
	}
	
	public void setUriMd5SubmissionFormId(String value) {
		if ( !setStringField(URI_MD5_SUBMISSION_FORM_ID, value) ) {
			throw new IllegalStateException("overflow uriMd5SubmissionFormId");
		}
	}
	
	public String getSubmissionFormId() {
		return getStringField(SUBMISSION_FORM_ID);
	}
	
	public void setSubmissionFormId(String value) {
		if ( !setStringField(SUBMISSION_FORM_ID, value) ) {
			throw new IllegalStateException("overflow submissionFormId");
		}
	}

	public Long getSubmissionModelVersion() {
		return getLongField(SUBMISSION_MODEL_VERSION);
	}

	public void setSubmissionModelVersion(Long value) {
		setLongField(SUBMISSION_MODEL_VERSION, value);
	}

	public Long getSubmissionUiVersion() {
		return getLongField(SUBMISSION_UI_VERSION);
	}

	public void setSubmissionUiVersion(Long value) {
		setLongField(SUBMISSION_UI_VERSION, value);
	}

	public Boolean getIsPersistenceModelComplete() {
		return getBooleanField(IS_PERSISTENCE_MODEL_COMPLETE);
	}

	public void setIsPersistenceModelComplete(Boolean value) {
		setBooleanField(IS_PERSISTENCE_MODEL_COMPLETE, value);
	}

	public Boolean getIsSubmissionAllowed() {
		return getBooleanField(IS_SUBMISSION_ALLOWED);
	}

	public void setIsSubmissionAllowed(Boolean value) {
		setBooleanField(IS_SUBMISSION_ALLOWED, value);
	}

	public String getUriSubmissionDataModel() {
		return getStringField(URI_SUBMISSION_DATA_MODEL);
	}

	public void setUriSubmissionDataModel(String value) {
		if ( !setStringField(URI_SUBMISSION_DATA_MODEL, value) ) {
			throw new IllegalStateException("overflow uriSubmissionDataModel");
		}
	}

	private static SubmissionAssociationTable relation = null;
	
	public static synchronized final SubmissionAssociationTable assertRelation(CallingContext cc) throws ODKDatastoreException {
		if ( relation == null ) {
			Datastore ds = cc.getDatastore();
			User user = cc.getCurrentUser();
			SubmissionAssociationTable relationPrototype;
			relationPrototype = new SubmissionAssociationTable(ds.getDefaultSchemaName());
			ds.assertRelation(relationPrototype, user); // may throw exception...
		    // at this point, the prototype has become fully populated
		    relation = relationPrototype; // set static variable only upon success...
		}
		return relation;
	}
	
	public static final List<SubmissionAssociationTable> findSubmissionAssociationsForXForm(XFormParameters params, CallingContext cc) {
	    List<SubmissionAssociationTable> saList = new ArrayList<SubmissionAssociationTable>();
		try {
			// changes here should be paralleled in the FormParserForJavaRosa
		    SubmissionAssociationTable saRelation = SubmissionAssociationTable.assertRelation(cc);
		    String submissionFormIdUri = CommonFieldsBase.newMD5HashUri(params.formId); // key under which submission is located...
		    Query q = cc.getDatastore().createQuery(saRelation, cc.getCurrentUser());
		    q.addFilter(SubmissionAssociationTable.URI_MD5_SUBMISSION_FORM_ID, Query.FilterOperation.EQUAL, submissionFormIdUri);
		    List<? extends CommonFieldsBase> l = q.executeQuery(0);
		    for ( CommonFieldsBase b : l ) {
		    	SubmissionAssociationTable t = (SubmissionAssociationTable) b;
		    	if ( t.getXFormParameters().equals(params) ) {
		    		saList.add(t);
		    	}
		    }
		    
		    if ( saList.size() > 1 ) {
		    	throw new IllegalStateException("Logic is not yet in place for cross-form submission sharing");
		    }
		    return saList;

		} catch ( ODKDatastoreException e) {
			e.printStackTrace();
		}
		return saList;
	}
}