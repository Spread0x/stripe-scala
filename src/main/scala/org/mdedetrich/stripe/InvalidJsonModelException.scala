package org.mdedetrich.stripe

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsValue}


/**
  * This exception is thrown when there is an error in converting the JSON to a case class
  * @param url The URL for the call
  * @param postParameters The POST body as form parameters
*   @param postJson The POST body as JSON
  * @param jsonResponse The original json response
  * @param errors The errors as reported from play-json
  */
case class InvalidJsonModelException(val httpStatusCode: Long,
                                     val url: String,
                                     val postParameters: Option[Map[String,String]],
                                     val postJson: Option[JsValue],
                                     val jsonResponse: JsValue,
                                     val errors: Seq[(JsPath, Seq[ValidationError])]) extends Exception {
  override def getMessage = s"Invalid JSON model, errors are $errors"
}
