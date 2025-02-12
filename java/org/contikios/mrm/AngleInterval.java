/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.mrm;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class represents an angle interval.
 * 
 * @author Fredrik Osterlind
 */
class AngleInterval {
  private static final Logger logger = LoggerFactory.getLogger(AngleInterval.class);
  
  // Sub intervals all between 0 and 2*PI
  final List<Interval> subIntervals = new ArrayList<>();
  
  /**
   * Creates a new angle interval.
   * 
   * @param startAngle Start angle (rad)
   * @param endAngle End angle (rad) (> start angle)
   */
  public AngleInterval(double startAngle, double endAngle) {
    if (endAngle < startAngle) {
      
    } else if (endAngle - startAngle >= 2*Math.PI) {
      subIntervals.add(new Interval(0, 2*Math.PI));
    } else {
      while (startAngle < 0)
        startAngle += 2*Math.PI;
      while (endAngle < 0)
        endAngle += 2*Math.PI;
      startAngle %= 2*Math.PI;
      endAngle %= 2*Math.PI;
      
      Interval tempInterval;
      if (startAngle < endAngle) {
        tempInterval = new Interval(startAngle, endAngle);
      } else {
        tempInterval = new Interval(startAngle, 2*Math.PI);
        if (!tempInterval.isEmpty())
          subIntervals.add(tempInterval);
        tempInterval = new Interval(0, endAngle);
      }
      if (!tempInterval.isEmpty())
        subIntervals.add(tempInterval);
    }
  }
  
  /**
   * Returns new intervals consisting of this interval with the given interval removed.
   * These can either be null (if entire interval was removed), 
   * one interval (if upper or lower part, or nothing was removed) or two intervals
   * (if middle part of interval was removed).
   * 
   * @param intervalToSubtract Other interval
   * @return New intervals
   */
  public List<AngleInterval> subtract(AngleInterval intervalToSubtract) {
    // Before subtraction
    var afterSubtractionIntervals = new ArrayList<>(subIntervals);
    
    if (intervalToSubtract == null) {
      var ret = new ArrayList<AngleInterval>();
      ret.add(this);
      return ret;
    }
    
    // Subtract every subinterval each
    for (int i=0; i < intervalToSubtract.subIntervals.size(); i++) {
      Interval subIntervalToSubtract = intervalToSubtract.subIntervals.get(i);
      var newAfterSubtractionIntervals = new ArrayList<Interval>();
      for (var afterSubtractionInterval : afterSubtractionIntervals) {
        var tempIntervals = afterSubtractionInterval.subtract(subIntervalToSubtract);
        if (tempIntervals != null)
          newAfterSubtractionIntervals.addAll(tempIntervals);
      }
      
      afterSubtractionIntervals = newAfterSubtractionIntervals;
    }
    
    var newAngleIntervals = new ArrayList<AngleInterval>();
    for (var afterSubtractionInterval : afterSubtractionIntervals) {
      if (afterSubtractionInterval != null && !afterSubtractionInterval.isEmpty())
        newAngleIntervals.add(
            new AngleInterval(afterSubtractionInterval.getLow(), afterSubtractionInterval.getHigh())
        );
    }
    
    return newAngleIntervals;
  }
  
  /**
   * Returns the intersection of this interval with
   * the given.
   * 
   * @param interval Other interval
   * @return Intersection
   */
  public AngleInterval intersectWith(AngleInterval interval) {
    var afterIntersectionIntervals = new ArrayList<Interval>();
    // Intersect all subintervals, keep all results
    for (int i=0; i < interval.subIntervals.size(); i++) {
      for (int j=0; j < subIntervals.size(); j++) {
        Interval temp = interval.subIntervals.get(i).intersectWith(subIntervals.get(j));
        if (temp != null && !temp.isEmpty())
          afterIntersectionIntervals.add(temp);
      }
    }
    
    if (afterIntersectionIntervals.size() > 2) {
      logger.error("AngleInterval.intersectWith() error!");
    } else if (afterIntersectionIntervals.size() == 2) {
      
      // The interval (y-x) is divided into:
      //  y -> 2*PI
      //  0 -> x
      Interval interval1 = afterIntersectionIntervals.get(0);
      Interval interval2 = afterIntersectionIntervals.get(1);
      
      if (interval1.getLow() == 0)
        return new AngleInterval(
            interval2.getLow(), interval1.getHigh() + 2*Math.PI
        );
      else
        return new AngleInterval(
            interval1.getLow(), interval2.getHigh() + 2*Math.PI
        );
      
    } else if (afterIntersectionIntervals.size() == 1) {
      return new AngleInterval(
          afterIntersectionIntervals.get(0).getLow(),
          afterIntersectionIntervals.get(0).getHigh()
      ); 
    }
    
    return null;
  }
  
  /**
   * Returns start angle of this interval.
   * This angle is always > 0 and < the end angle.
   * 
   * @return Start angle
   */
  public double getStartAngle() {
    if (subIntervals == null || subIntervals.isEmpty()) {
      logger.warn("Getting start angle of null angle interval!");
      return 0;
    }
    
    if (subIntervals.size() > 2) {
      logger.warn("Getting start angle of malformed angle interval!");
      return 0;
    }
    
    if (subIntervals.size() == 1) {
      return subIntervals.get(0).getLow();
    }
    
    // The interval (y-x) is divided into:
    //  y -> 2*PI
    //  0 -> x
    Interval interval1 = subIntervals.get(0);
    Interval interval2 = subIntervals.get(1);
    
    if (interval1.getLow() == 0)
      return interval2.getLow();
    else
      return interval1.getLow();
  }
  
  /**
   * Returns end angle of this interval.
   * This angle is always > start angle, and may be > 2*PI.
   * 
   * @return End angle
   */
  public double getEndAngle() {
    if (subIntervals == null || subIntervals.isEmpty()) {
      logger.warn("Getting start angle of null angle interval!");
      return 0;
    }
    
    if (subIntervals.size() > 2) {
      logger.warn("Getting start angle of malformed angle interval!");
      return 0;
    }
    
    if (subIntervals.size() == 1) {
      return subIntervals.get(0).getHigh();
    }
    
    // The interval (y-x) is divided into:
    //  y -> 2*PI
    //  0 -> x
    Interval interval1 = subIntervals.get(0);
    Interval interval2 = subIntervals.get(1);
    
    if (interval1.getLow() == 0)
      return interval1.getHigh() + 2*Math.PI;
    else
      return interval2.getHigh() + 2*Math.PI;
  }
  
  /**
   * @return Size of interval (rad)
   */
  public double getSize() {
    double size = 0;
    for (var subInterval : subIntervals) size += subInterval.getSize();
    return size;
  }
  
  /**
   * Checks if the given interval is a subset of this interval.
   * 
   * @param interval Other interval
   * @return True if this interval contains given interval
   */
  public boolean contains(AngleInterval interval) {
    // Check that all parts of argument is contained by any part of this
    for (int i=0; i < interval.subIntervals.size(); i++) {
      boolean contained = false;
      for (int j=0; j < subIntervals.size(); j++) {
        if (subIntervals.get(j).contains(interval.subIntervals.get(i))) {
          contained = true;
          break;
        }
      }
      if (!contained)
        return false;
    }
    return true;
  }
  
  /**
   * Checks if the two intervals intersect.
   * 
   * @param interval Other interval
   * @return True if this interval intersects given interval
   */
  public boolean intersects(AngleInterval interval) {
    return (intersectWith(interval) != null);
  }
  
  /**
   * @return True if interval defined is of no size.
   */
  public boolean isEmpty() {
    if (subIntervals.isEmpty())
      return true;
    return getSize() <= 0.001;
  }
  
  @Override
  public String toString() {
    var retString = new StringBuilder();
    for (var subInterval : subIntervals) {
      if (!retString.isEmpty())
        retString.append(" && ");
      retString.append('(').append(Math.toDegrees(subInterval.getLow())).append(" -> ")
              .append(Math.toDegrees(subInterval.getHigh())).append(')');
    }
    return retString.toString();
  }
  
  /**
   * Returns a line starting at given start point and
   * in the given direction.
   * This line may be used when calculating intersection points.
   * 
   * @param startPoint Start point
   * @param angle Line direction (rad)
   * @return Line
   */
  public static Line2D getDirectedLine(Point2D startPoint, double angle, double length) {
    return new Line2D.Double(
        startPoint.getX(),
        startPoint.getY(),
        startPoint.getX() + length*Math.cos(angle),
        startPoint.getY() + length*Math.sin(angle)
    );
  }
  
  /**
   * Returns an angle interval of the given line seen from
   * the given reference point.
   * 
   * @param refPoint Reference point
   * @param line Line to measure angle against
   * @return Angle interval (-pi <-> pi)
   */
  public static AngleInterval getAngleIntervalOfLine(Point2D refPoint, Line2D line) {
    // Create angle interval of this line
    double x1 = line.getX1() - refPoint.getX(); 
    double y1 = line.getY1() - refPoint.getY(); 
    double x2 = line.getX2() - refPoint.getX(); 
    double y2 = line.getY2() - refPoint.getY(); 
    
    double angle1 = Math.atan2(y1, x1);
    double angle2 = Math.atan2(y2, x2);
    
    // If interval is bigger than PI, line angles must wrap
    if (Math.abs(angle1 - angle2) > Math.PI) {
      if (angle1 < 0)
        angle1 += 2*Math.PI;
      else
        angle2 += 2*Math.PI;
    }
    
    return new AngleInterval(
        Math.min(angle1, angle2), Math.max(angle1, angle2)
    );
  }
  
  @Override
  public boolean equals(Object object) {
    if (object == null)
      return false;
    
    AngleInterval interval = (AngleInterval) object;
    return (interval.getStartAngle() == this.getStartAngle() && interval.getEndAngle() == this.getEndAngle());
  }
  
  /**
   * Subtracts given interval from all intervals in given vector.
   * This method never returns null (but empty vectors).
   * 
   * @param initialIntervals Initial intervals
   * @param interval Interval to subtract
   * @return New intervals
   */
  public static List<AngleInterval> subtract(List<AngleInterval> initialIntervals, AngleInterval interval) {
    var newIntervals = new ArrayList<AngleInterval>();
    for (var initialInterval : initialIntervals) {
      var tempIntervals = initialInterval.subtract(interval);
      if (tempIntervals != null) {
        newIntervals.addAll(tempIntervals);
      }
    }
    
    return newIntervals;
  }
  
  /**
   * Intersects given interval with all intervals in given vector.
   * This method never returns null (but empty vectors).
   * 
   * @param initialIntervals Initial intervals
   * @param interval Interval to intersect
   * @return New intervals
   */
  public static List<AngleInterval> intersect(List<AngleInterval> initialIntervals, AngleInterval interval) {
    var newIntervals = new ArrayList<AngleInterval>();
    for (var initialInterval : initialIntervals) {
      AngleInterval tempInterval = initialInterval.intersectWith(interval);
      if (tempInterval != null) {
        newIntervals.add(tempInterval);
      }
    }
    
    return newIntervals;
  }
  
  
}
