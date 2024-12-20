// Copyright (c) 2011  Deschutes Signal Processing LLC
// Author:  David B. Harris

//  This file is part of OregonDSP.
//
//    OregonDSP is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Lesser General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    OregonDSP is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Lesser General Public License for more details.
//
//    You should have received a copy of the GNU Lesser General Public License
//    along with OregonDSP.  If not, see <http://www.gnu.org/licenses/>.

package com.hcifuture.producer.detector.signal.filter.fir.equiripple


/**
 * Implements a centered FIR bandpass filter - the point of symmetry falls on a sample.

 *
 * This class uses the Remez exchange algorithm to design a bandpass filter of length 2N+1.
 * The impulse response is a symmetric sequence about point N (counting from 0). The filter is linear
 * phase, with group delay a constant equal to N.  The design parameters are the _order (N) specifying
 * the number (N+1) of approximating functions in the Remez algorithm and seven parameters controlling
 * the cutoffs and design weights of the passband and 2 stopbands.  The design problem is performed on
 * a discrete-time frequency axis normalized to range between 0 and 1 (the folding frequency).  The
 * pass band is the interval [OmegaP1, OmegaP2].  The lower stop band is the interval [0, OmegaS1].
 * The upper stop band is the interval [OmegaS2, 1].  Note that 0 < OmegaS1 < OmegaP1 < OmegaP2 <
 * OmegaS2 < 1.  All bands must have non-zero width, and the open intervals (transition bands)
 * (OmegaS1, OmegaP1) and (OmegaP2, OmegaS2) also must have non-zero width. The narrower any of these
 * bands, the larger the _order N must be to obtain a reasonable frequency response.  Weights are
 * specified for each band to control the relative sizes of the maximum errors among bands.
 * For details on the design algorithm and characteristics of the filter response, see

 *
 * A Unified Approach to the Design of Optimum Linear Phase FIR Digital Filters,
 * James H. McClellan and Thomas W. Parks (1973), IEEE Transactions on Circuit Theory, Vol. CT-20,
 * No. 6, pp. 697-701.

 * and

 *
 * FIR Digital Filter Design Techniques Using Weighted Chebyshev Approximation,
 * Lawrence R. Rabiner, James H. McClellan and Thomas W. Parks (1975) PROCEEDINGS OF THE IEEE,
 * VOL. 63, NO. 4, pp. 595-610.

 *
 * and for _order selection, consult:

 *
 * Approximate Design Relationships for Low-Pass FIR Digital Filters, Lawrence R. Rabiner (1973),
 * IEEE TRANSACTIONS ON AUDIO AND ELECTROACOUSTICS, VOL. AU-21, NO. 5, pp. 456-460.

 * @author David B. Harris,   Deschutes Signal Processing LLC
 */
class EquirippleBandpass
/**
 * Instantiates a new equiripple bandpass FIR filter.

 * @param N         int specifying the design _order of the filter.
 * *
 * @param OmegaS1   double specifying the upper cutoff of the low stopband.
 * *
 * @param Ws1       double specifying the error weighting of the lower stopband.
 * *
 * @param OmegaP1   double specifying the lower cutoff of the passband.
 * *
 * @param OmegaP2   double specifying the upper cutoff of the passband.
 * *
 * @param Wp        double specifying the error weighting of the passband.
 * *
 * @param OmegaS2   double specifying the lower cutoff of the high stopband.
 * *
 * @param Ws2       double specifying the error weighting of the upper stopband.
 */
(N: Int, OmegaS1: Double,
 /** double specifying the lower stopband weight.  */
 private val Ws1: Double, OmegaP1: Double, OmegaP2: Double,
 /** double specifying the passband weight.  */
 private val Wp: Double, OmegaS2: Double,
 /** double specifying the upper stopband weight.  */
 private val Ws2: Double) : FIRTypeI(3, N) {

    init {

        if (!(0.0 < OmegaS1 &&
                OmegaS1 < OmegaP1 &&
                OmegaP1 < OmegaP2 &&
                OmegaP2 < OmegaS2 &&
                OmegaS2 < 1.0))
            throw IllegalArgumentException("Band edge specification error, ensure that 0.0 < OmegaS1 < OmegaP1 < OmegaP2 < OmegaS2 < 1.0")

        bands[0][0] = 0.0
        bands[0][1] = OmegaS1
        bands[1][0] = OmegaP1
        bands[1][1] = OmegaP2
        bands[2][0] = OmegaS2
        bands[2][1] = 1.0

        generateCoefficients()
    }


    /* (non-Javadoc)
   * @see com.oregondsp.signalProcessing.filter.fir.equiripple.EquirippleFIRFilter#desiredResponse(double)
   */
    internal override fun desiredResponse(Omega: Double): Double {

        var retval = 0.0
        if (LTE(bands[1][0], Omega) && LTE(Omega, bands[1][1])) retval = 1.0

        return retval
    }


    /* (non-Javadoc)
   * @see com.oregondsp.signalProcessing.filter.fir.equiripple.EquirippleFIRFilter#weight(double)
   */
    internal override fun weight(Omega: Double): Double {

        var retval = 0.0

        if (LTE(bands[0][0], Omega) && LTE(Omega, bands[0][1]))
            retval = Ws1
        else if (LTE(bands[1][0], Omega) && LTE(Omega, bands[1][1]))
            retval = Wp
        else if (LTE(bands[2][0], Omega) && LTE(Omega, bands[2][1]))
            retval = Ws2

        return retval
    }

}
