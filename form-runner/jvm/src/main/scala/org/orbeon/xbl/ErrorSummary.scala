/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

object ErrorSummary {

  private val ErrorSummaryIds = List("error-summary-control-top", "error-summary-control-bottom")

  private def findErrorSummaryControl = (
    ErrorSummaryIds
    flatMap      { id ⇒ Option(inScopeContainingDocument.getControlByEffectiveId(id)) }
    collectFirst { case c: XFormsComponentControl ⇒ c }
  )

  private def findErrorSummaryModel =
    findErrorSummaryControl flatMap (_.nestedContainer.models find (_.getId == "fr-error-summary-model"))

  private def findErrorsInstance =
    findErrorSummaryModel map (_.getInstance("fr-errors-instance"))

  //@XPathFunction
  def topLevelSectionNameForControlId(absoluteControlId: String): Option[String] =
    Option(inScopeContainingDocument.getControlByEffectiveId(XFormsId.absoluteIdToEffectiveId(absoluteControlId))) flatMap {
      control ⇒
        val sectionsIt =
          new AncestorOrSelfIterator(control) collect {
            case section: XFormsComponentControl if section.localName == "section" ⇒ section
          }

        sectionsIt.lastOption() map (_.getId) flatMap FormRunner.controlNameFromIdOpt
    }

  // Return the subset of section names passed which contain errors in the global error summary
  //@XPathFunction
  def topLevelSectionsWithErrors(sectionNames: String, onlyVisible: Boolean): Seq[String] =
    findErrorsInstance match {
      case Some(errorsInstance) ⇒

        val sectionNamesSet = sectionNames.splitTo[Set]()

        def allErrorsIt =
          (errorsInstance.rootElement / "error").iterator

        def visibleErrorsIt =
          findErrorSummaryModel.iterator flatMap (m ⇒ asScalaIterator(m.getVariable("visible-errors"))) collect {
            case n: NodeInfo ⇒ n
          }

        val relevantErrorsIt =
          (if (onlyVisible) visibleErrorsIt else allErrorsIt) filter (_.attValue("level") == "error")

        val sectionNameErrorIt = (
          relevantErrorsIt
          map    { e ⇒ e.attValue("section-name") → e }
          filter { case (name, _) ⇒ sectionNamesSet(name) }
        )

        val groupedIt =
          sectionNameErrorIt.to[List] groupBy (_._1) iterator

        val sectionNameCountIt =
          groupedIt map { case (name, list) ⇒ name → list.size }

        sectionNameCountIt.map(_._1).to[List]

      case None ⇒
        Nil
    }

  // Update the iteration in a control's absolute id
  //@XPathFunction
  def updateIteration(absoluteId: String, repeatAbsoluteId: String, fromIterations: Array[Int], toIterations: Array[Int]): String = {

    val effectiveId = XFormsId.absoluteIdToEffectiveId(absoluteId)
    val prefixedId  = XFormsId.getPrefixedId(effectiveId)

    val repeatEffectiveId = XFormsId.absoluteIdToEffectiveId(repeatAbsoluteId)
    val repeatPrefixedId  = XFormsId.getPrefixedId(repeatEffectiveId)

    val ancestorRepeats = inScopeContainingDocument.getStaticOps.getAncestorRepeatIds(prefixedId)

    if (ancestorRepeats contains repeatPrefixedId) {
      // Control is a descendant of the repeat so might be impacted

      val idIterationPairs = XFormsId.getEffectiveIdSuffixParts(effectiveId) zip ancestorRepeats
      val iterationsMap    = fromIterations zip toIterations toMap

      val newIterations = idIterationPairs map {
        case (fromIteration, `repeatPrefixedId`) if iterationsMap.contains(fromIteration) ⇒ iterationsMap(fromIteration).toString.asInstanceOf[AnyRef]
        case (iteration, _)                                                               ⇒ iteration.toString.asInstanceOf[AnyRef]
      }

      val newEffectiveId = XFormsId.buildEffectiveId(prefixedId, newIterations)

      XFormsId.effectiveIdToAbsoluteId(newEffectiveId)

    } else
      absoluteId // id is not impacted
  }

  private val Digits = "0" * 5

  // Return a sorting string for the given control absolute id, taking repeats into account
  //@XPathFunction
  def controlSortString(absoluteId: String, repeatsDepth: Int): String = {

    val effectiveId = XFormsId.absoluteIdToEffectiveId(absoluteId)
    val prefixedId  = XFormsId.getPrefixedId(effectiveId)

    val controlPosition =
      inScopeContainingDocument.getStaticOps.getControlPosition(prefixedId).get // argument must be a view control

    val repeatsFromLeaf =
      inScopeContainingDocument.getStaticOps.getAncestorRepeats(prefixedId)

    def iterations =
      XFormsId.getEffectiveIdSuffixParts(effectiveId)

    // Use arrays indexes to *attempt* to be more efficient
    // NOTE: Profiler shows that the 2 calls to ofDim take 50% of the method time
    val result = Array.ofDim[Int](repeatsDepth * 2 + 1)

    locally {
      var i = (repeatsFromLeaf.size - 1) * 2
      for (r ← repeatsFromLeaf) {
        result(i) = r.index
        i -= 2
      }
    }

    locally {
      var i = 1
      for (iteration ← iterations) {
        result(i) = iteration
        i += 2
      }
    }

    result(repeatsFromLeaf.size * 2) = controlPosition

    def padWithZeros(i: Int) = {
      val s    = i.toString
      val diff = Digits.length - s.length

      if (diff > 0) Digits.substring(0, diff) + s else s
    }

    val resultString = Array.ofDim[String](result.length)

    for (i ← result.indices)
      resultString(i) = padWithZeros(result(i))

    resultString mkString "-"
  }
}
