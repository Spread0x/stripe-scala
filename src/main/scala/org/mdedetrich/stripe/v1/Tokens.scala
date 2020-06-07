package org.mdedetrich.stripe.v1

import java.time.OffsetDateTime
import akka.stream._

import akka.http.scaladsl.HttpExt
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import defaults._
import enumeratum._
import io.circe.{Decoder, Encoder}
import org.mdedetrich.stripe.{ApiKey, Endpoint, IdempotencyKey, PostParams}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * @see https://stripe.com/docs/api#tokens
  */
object Tokens extends LazyLogging {

  sealed abstract class Type(val id: String) extends EnumEntry {
    override val entryName = id
  }

  object Type extends Enum[Type] {
    val values = findValues

    case object Card        extends Type("card")
    case object BankAccount extends Type("bank_account")
    case object Pii         extends Type("pii")

    implicit val tokenTypeDecoder: Decoder[Type] = enumeratum.Circe.decoder(Type)
    implicit val tokenTypeEncoder: Encoder[Type] = enumeratum.Circe.encoder(Type)
  }

  /**
    * @see https://stripe.com/docs/api#retrieve_token
    * @param id
    * @param created
    * @param livemode
    * @param `type`      Type of the token: [[Type.Card]] or [[Type.BankAccount]]
    * @param used        Whether or not this token has already
    *                    been used (tokens can be used only once)
    * @param bankAccount Hash describing the bank account
    * @param card        Hash describing the card used to make the charge
    * @param clientIp    IP address of the client that generated the token
    */
  final case class Token(
      id: String,
      created: OffsetDateTime,
      livemode: Boolean,
      `type`: Type,
      used: Boolean,
      bankAccount: Option[BankAccounts.BankAccount] = None,
      card: Option[Cards.Card] = None,
      clientIp: Option[String] = None
  )

  implicit val tokenDecoder: Decoder[Token] = Decoder.forProduct8(
    "id",
    "created",
    "livemode",
    "type",
    "used",
    "bank_account",
    "card",
    "client_ip"
  )(Token.apply)

  implicit val tokenEncoder: Encoder[Token] = Encoder.forProduct9(
    "id",
    "object",
    "created",
    "livemode",
    "type",
    "used",
    "bank_account",
    "card",
    "client_ip"
  )(
    x =>
      (
        x.id,
        "token",
        x.bankAccount,
        x.card,
        x.clientIp,
        x.created,
        x.livemode,
        x.`type`,
        x.used
      )
  )

  sealed abstract class TokenData

  object TokenData {

    /** Creates a single use token that wraps the details of a credit card.
      * This token can be used in place of a credit card dictionary with
      * any API method. These tokens can only be used once: by creating a
      * new charge object, or attaching them to a customer.
      *
      * @see https://stripe.com/docs/api#create_card_token
      * @param expMonth Two digit number representing
      *                 the card's expiration month.
      * @param expYear  Two or four digit number representing
      *                 the card's expiration year.
      * @param number   The card number, as a string
      *                 without any separators.
      * @param addressCity
      * @param addressCountry
      * @param addressLine1
      * @param addressLine2
      * @param addressState
      * @param addressZip
      * @param currency Required to be able to add the card to an
      *                 account (in all other cases, this parameter is not used).
      *                 When added to an account, the card (which must
      *                 be a debit card) can be used as a transfer
      *                 destination for funds in this currency.
      *                 Currently, the only supported currency
      *                 for debit card transfers is usd.
      * @param cvc      Card security code. Required unless your
      *                 account is registered in Australia, Canada,
      *                 or the United States. Highly recommended to
      *                 always include this value.
      * @param name     Cardholder's full name.
      */
    final case class Card(
        expMonth: Int,
        expYear: Int,
        number: String,
        addressCity: Option[String] = None,
        addressCountry: Option[String] = None,
        addressLine1: Option[String] = None,
        addressLine2: Option[String] = None,
        addressState: Option[String] = None,
        addressZip: Option[String] = None,
        currency: Option[Currency] = None,
        cvc: Option[String] = None,
        name: Option[String] = None
    ) extends TokenData

    implicit val cardDecoder: Decoder[Card] = Decoder.forProduct12(
      "exp_month",
      "exp_year",
      "number",
      "address_city",
      "address_country",
      "address_line1",
      "address_line2",
      "address_state",
      "address_zip",
      "currency",
      "cvc",
      "name"
    )(Card.apply)

    implicit val cardEncoder: Encoder[Card] = Encoder.forProduct12(
      "exp_month",
      "exp_year",
      "number",
      "address_city",
      "address_country",
      "address_line1",
      "address_line2",
      "address_state",
      "address_zip",
      "currency",
      "cvc",
      "name"
    )(
      x =>
        (
          x.expMonth,
          x.expYear,
          x.number,
          x.addressCity,
          x.addressCountry,
          x.addressLine1,
          x.addressLine2,
          x.addressState,
          x.addressZip,
          x.currency,
          x.cvc,
          x.name
        )
    )

    /** Creates a single use token that wraps the details of a bank account.
      * This token can be used in place of a bank account dictionary with
      * any API method. These tokens can only be used once: by attaching
      * them to a recipient or managed account.
      *
      * @see https://stripe.com/docs/api#create_bank_account_token
      * @param accountNumber     The account number for the bank account
      *                          in string form. Must be a checking account.
      * @param country           The country the bank account is in.
      * @param currency          The currency the bank account is in. This
      *                          must be a country/currency pairing
      *                          that Stripe supports.
      * @param routingNumber     The routing number, sort code, or other
      *                          country-appropriate institution number for the
      *                          bank account. For US bank accounts, this is
      *                          required and should be the ACH routing number,
      *                          not the wire routing number. If you are providing
      *                          an IBAN for [[accountNumber]], this field is
      *                          not required.
      * @param accountHolderName The name of the person or business that owns
      *                          the bank account. This field is required when
      *                          attaching the bank account to a customer object.
      * @param accountHolderType The type of entity that holds the account. This
      *                          can be either "individual" or "company". This field
      *                          is required when attaching the bank account to
      *                          a customer object.
      */
    final case class BankAccount(
        accountNumber: String,
        country: String,
        currency: Currency,
        routingNumber: Option[String] = None,
        accountHolderName: Option[String] = None,
        accountHolderType: Option[BankAccounts.AccountHolderType] = None
    ) extends TokenData

    implicit val bankAccountDecoder: Decoder[BankAccount] = Decoder.forProduct6(
      "account_number",
      "country",
      "currency",
      "routing_number",
      "account_holder_name",
      "account_holder_type"
    )(BankAccount.apply)

    implicit val bankAccountEncoder: Encoder[BankAccount] = Encoder.forProduct6(
      "account_number",
      "country",
      "currency",
      "routing_number",
      "account_holder_name",
      "account_holder_type"
    )(x => (x.accountNumber, x.country, x.currency, x.routingNumber, x.accountHolderName, x.accountHolderType))

    /** Creates a single use token that wraps the details of personally
      * identifiable information (PII). This token can be used in place
      * of a [[personalIdNumber]] in the Account Update API method. These
      * tokens can only be used once.
      *
      * @see https://stripe.com/docs/api#create_pii_token
      * @param personalIdNumber The [[personalIdNumber]] for PII
      *                         in string form.
      * @param pii              The PII this token will represent.
      */
    final case class PII(personalIdNumber: String, pii: Option[String]) extends TokenData

    implicit val PIIDecoder: Decoder[PII] =
      Decoder.forProduct2("personal_id_number", "pii")(PII.apply)

    implicit val PIIEncoder: Encoder[PII] =
      Encoder.forProduct2("personal_id_number", "pii")(x => PII.unapply(x).get)
  }

  final case class TokenInput(tokenData: TokenData, customer: Option[String] = None)

  def create(tokenInput: TokenInput)(idempotencyKey: Option[IdempotencyKey] = None)(
      implicit apiKey: ApiKey,
      endpoint: Endpoint,
      client: HttpExt,
      materializer: Materializer,
      executionContext: ExecutionContext
  ): Future[Try[Token]] = {
    val postFormParameters: Map[String, String] = {
      PostParams.flatten(
        Map(
          "customer" -> tokenInput.customer
        )
      ) ++ {
        tokenInput.tokenData match {
          case card: TokenData.Card =>
            val map = PostParams.flatten(
              Map(
                "exp_month"       -> Some(card.expMonth.toString),
                "exp_year"        -> Some(card.expYear.toString),
                "number"          -> Some(card.number),
                "address_city"    -> card.addressCity,
                "address_country" -> card.addressCountry,
                "address_line1"   -> card.addressLine1,
                "address_line2"   -> card.addressLine2,
                "address_state"   -> card.addressState,
                "address_zip"     -> card.addressZip,
                "currency"        -> card.currency.map(_.iso.toLowerCase),
                "cvc"             -> card.cvc,
                "name"            -> card.name
              )
            )
            mapToPostParams(Some(map), "card")
          case bankAccount: TokenData.BankAccount =>
            val map = PostParams.flatten(
              Map(
                "account_number"      -> Some(bankAccount.accountNumber),
                "country"             -> Some(bankAccount.country),
                "currency"            -> Some(bankAccount.currency.iso.toLowerCase),
                "routing_number"      -> bankAccount.routingNumber,
                "account_holder_name" -> bankAccount.accountHolderName,
                "account_holder_type" -> bankAccount.accountHolderType.map(_.id)
              )
            )
            mapToPostParams(Some(map), "bank_account")
          case pii: TokenData.PII =>
            val map = PostParams.flatten(
              Map(
                "pii"                -> pii.pii,
                "personal_id_number" -> Some(pii.personalIdNumber)
              )
            )
            mapToPostParams(Some(map), "pii")
        }
      }
    }

    logger.debug(s"Generated POST form parameters is $postFormParameters")

    val finalUrl = endpoint.url + s"/v1/tokens"

    createRequestPOST[Token](finalUrl, postFormParameters, idempotencyKey, logger)
  }

  def get(id: String)(
      implicit apiKey: ApiKey,
      endpoint: Endpoint,
      client: HttpExt,
      materializer: Materializer,
      executionContext: ExecutionContext
  ): Future[Try[Token]] = {
    val finalUrl = endpoint.url + s"/v1/tokens/$id"

    createRequestGET[Token](finalUrl, logger)
  }
}
