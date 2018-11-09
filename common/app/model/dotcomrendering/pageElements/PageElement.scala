package model.dotcomrendering.pageElements

import com.gu.contentapi.client.model.v1.ElementType.{Map => _, _}
import com.gu.contentapi.client.model.v1.{ElementType, SponsorshipType, BlockElement => ApiBlockElement, Sponsorship => ApiSponsorship}
import model.{AudioAsset, ImageAsset, ImageMedia, VideoAsset}
import play.api.libs.json._
import views.support.ImageUrlSigner

/*
  These elements are used for the Dotcom Rendering, they are essentially the new version of the
  model.liveblog._ elements but replaced in full here
 */

sealed trait PageElement
case class TextBlockElement(html: Option[String]) extends PageElement
case class TweetBlockElement(html: Option[String]) extends PageElement
case class PullquoteBlockElement(html: Option[String]) extends PageElement
case class ImageBlockElement(media: ImageMedia, data: Map[String, String], displayCredit: Option[Boolean]) extends PageElement
case class AudioBlockElement(assets: Seq[AudioAsset]) extends PageElement
case class GuVideoBlockElement(assets: Seq[VideoAsset], imageMedia: ImageMedia, data: Map[String, String]) extends PageElement
case class VideoBlockElement(data: Map[String, String]) extends PageElement
case class EmbedBlockElement(html: Option[String], safe: Option[Boolean], alt: Option[String]) extends PageElement
case class ContentAtomBlockElement(atomId: String) extends PageElement
case class InteractiveBlockElement(html: Option[String]) extends PageElement
case class CommentBlockElement(html: Option[String]) extends PageElement
case class TableBlockElement(html: Option[String]) extends PageElement
case class WitnessBlockElement(html: Option[String]) extends PageElement
case class DocumentBlockElement(html: Option[String]) extends PageElement
case class InstagramBlockElement(html: Option[String]) extends PageElement
case class VineBlockElement(html: Option[String]) extends PageElement
case class MapBlockElement(html: Option[String]) extends PageElement
case class UnknownBlockElement(html: Option[String]) extends PageElement

case class MembershipBlockElement(
  originalUrl: Option[String],
  linkText: Option[String],
  linkPrefix: Option[String],
  title: Option[String],
  venue: Option[String],
  location: Option[String],
  identifier: Option[String],
  image: Option[String],
  price: Option[String]
) extends PageElement

// these don't appear to have typeData on the capi models so we just have empty html
case class CodeBlockElement(html: Option[String]) extends PageElement
case class FormBlockElement(html: Option[String]) extends PageElement

case class RichLinkBlockElement(
  url: Option[String],
  text: Option[String],
  prefix: Option[String],
  sponsorship: Option[Sponsorship]
) extends PageElement

case class Sponsorship (
  sponsorName: String,
  sponsorLogo: String,
  sponsorLink: String,
  sponsorshipType: SponsorshipType
)

object Sponsorship {
  def apply(sponsorship: ApiSponsorship): Sponsorship = {
    Sponsorship(
      sponsorship.sponsorName,
      sponsorship.sponsorLogo,
      sponsorship.sponsorLink,
      sponsorship.sponsorshipType
    )
  }
}

object PageElement {
  def make(element: ApiBlockElement): Option[PageElement] = {

    element.`type` match {

      case Text => Some(TextBlockElement(element.textTypeData.flatMap(_.html)))

      case Tweet => Some(TweetBlockElement(element.tweetTypeData.flatMap(_.html)))

      case RichLink => Some(RichLinkBlockElement(
        element.richLinkTypeData.flatMap(_.originalUrl),
        element.richLinkTypeData.flatMap(_.linkText),
        element.richLinkTypeData.flatMap(_.linkPrefix),
        element.richLinkTypeData.flatMap(_.sponsorship).map(Sponsorship(_))
      ))

      case Image => {
        val signedAssets = element.assets.zipWithIndex
          .map { case (a, i) => ImageAsset.make(a, i) }
          .map { asset =>
            asset.copy(url = asset.url.map(url => ImageUrlSigner.sign(url)))
          }
        Some(ImageBlockElement(
          ImageMedia(signedAssets),
          imageDataFor(element),
          element.imageTypeData.flatMap(_.displayCredit)
        ))
      }

      case Audio => Some(AudioBlockElement(element.assets.map(AudioAsset.make)))

      case Video =>
        if (element.assets.nonEmpty) {
          Some(GuVideoBlockElement(
            element.assets.map(VideoAsset.make),
            ImageMedia(element.assets.filter(_.mimeType.exists(_.startsWith("image"))).zipWithIndex.map {
              case (a, i) => ImageAsset.make(a, i)
            }),
            videoDataFor(element))
          )
        }
        else Some(VideoBlockElement(videoDataFor(element)))

      case Membership => element.membershipTypeData.map(m => MembershipBlockElement(
        m.originalUrl,
        m.linkText,
        m.linkPrefix,
        m.title,
        m.venue,
        m.location,
        m.identifier,
        m.image,
        m.price
      ))

      case Embed => element.embedTypeData.map(d => EmbedBlockElement(d.html, d.safeEmbedCode, d.alt))

      case Contentatom => element.contentAtomTypeData.map(d => ContentAtomBlockElement(d.atomId))

      case Pullquote => element.pullquoteTypeData.map(d => PullquoteBlockElement(d.html))
      case Interactive => element.interactiveTypeData.map(d => InteractiveBlockElement(d.html))
      case Comment => element.commentTypeData.map(d => CommentBlockElement(d.html))
      case Table => element.tableTypeData.map(d => TableBlockElement(d.html))
      case Witness => element.witnessTypeData.map(d => WitnessBlockElement(d.html))
      case Document => element.documentTypeData.map(d => DocumentBlockElement(d.html))
      case Instagram => element.instagramTypeData.map(d => InstagramBlockElement(d.html))
      case Vine => element.vineTypeData.map(d => VineBlockElement(d.html))
      case ElementType.Map => element.mapTypeData.map(d => MapBlockElement(d.html))
      case Code => Some(CodeBlockElement(None))
      case Form => Some(FormBlockElement(None))

      case EnumUnknownElementType(f) => Some(UnknownBlockElement(None))

    }
  }

  private def imageDataFor(element: ApiBlockElement): Map[String, String] = {
    element.imageTypeData.map { d => Map(
      "copyright" -> d.copyright,
      "alt" -> d.alt,
      "caption" -> d.caption,
      "credit" -> d.credit
    ) collect { case (k, Some(v)) => (k, v) }
    } getOrElse Map()
  }

  private def videoDataFor(element: ApiBlockElement): Map[String, String] = {
    element.videoTypeData.map { d => Map(
      "caption" -> d.caption,
      "url" -> d.url
    ) collect { case (k, Some (v) ) => (k, v) }
    } getOrElse Map()
  }

  implicit val textBlockElementWrites: Writes[TextBlockElement] = Json.writes[TextBlockElement]
  implicit val ImageBlockElementWrites: Writes[ImageBlockElement] = Json.writes[ImageBlockElement]
  implicit val AudioBlockElementWrites: Writes[AudioBlockElement] = Json.writes[AudioBlockElement]
  implicit val GuVideoBlockElementWrites: Writes[GuVideoBlockElement] = Json.writes[GuVideoBlockElement]
  implicit val VideoBlockElementWrites: Writes[VideoBlockElement] = Json.writes[VideoBlockElement]
  implicit val TweetBlockElementWrites: Writes[TweetBlockElement] = Json.writes[TweetBlockElement]
  implicit val EmbedBlockElementWrites: Writes[EmbedBlockElement] = Json.writes[EmbedBlockElement]
  implicit val ContentAtomBlockElementWrites: Writes[ContentAtomBlockElement] = Json.writes[ContentAtomBlockElement]
  implicit val PullquoteBlockElementWrites: Writes[PullquoteBlockElement] = Json.writes[PullquoteBlockElement]
  implicit val InteractiveBlockElementWrites: Writes[InteractiveBlockElement] = Json.writes[InteractiveBlockElement]
  implicit val CommentBlockElementWrites: Writes[CommentBlockElement] = Json.writes[CommentBlockElement]
  implicit val TableBlockElementWrites: Writes[TableBlockElement] = Json.writes[TableBlockElement]
  implicit val WitnessBlockElementWrites: Writes[WitnessBlockElement] = Json.writes[WitnessBlockElement]
  implicit val DocumentBlockElementWrites: Writes[DocumentBlockElement] = Json.writes[DocumentBlockElement]
  implicit val InstagramBlockElementWrites: Writes[InstagramBlockElement] = Json.writes[InstagramBlockElement]
  implicit val VineBlockElementWrites: Writes[VineBlockElement] = Json.writes[VineBlockElement]
  implicit val MapBlockElementWrites: Writes[MapBlockElement] = Json.writes[MapBlockElement]
  implicit val CodeBlockElementWrites: Writes[CodeBlockElement] = Json.writes[CodeBlockElement]
  implicit val MembershipBlockElementWrites: Writes[MembershipBlockElement] = Json.writes[MembershipBlockElement]
  implicit val FormBlockElementWrites: Writes[FormBlockElement] = Json.writes[FormBlockElement]
  implicit val UnknownBlockElementWrites: Writes[UnknownBlockElement] = Json.writes[UnknownBlockElement]

  implicit val SponsorshipWrites: Writes[Sponsorship] = new Writes[Sponsorship] {
    def writes(sponsorship: Sponsorship): JsObject = Json.obj(
      "sponsorName" -> sponsorship.sponsorName,
      "sponsorLogo" -> sponsorship.sponsorLogo,
      "sponsorLink" -> sponsorship.sponsorLink,
      "sponsorshipType" -> sponsorship.sponsorshipType.name)
  }

  implicit val RichLinkBlockElementWrites: Writes[RichLinkBlockElement] = Json.writes[RichLinkBlockElement]
  val blockElementWrites: Writes[PageElement] = Json.writes[PageElement]

}
