package com.mackyi.cardboarddemo;

import java.util.Arrays;


public class Main {

	public static final float MARGIN_OF_ERROR = (float) 0.01;
	
	// returns null if no intersection
	public static float[] getIntersection(float[] points, float[] line) {
		float[] l1 = new float[3];
		float[] l2 = new float[3];
		
		l1[0] = line[0];
		l1[1] = line[1];
		l1[2] = line[2];

		l2[0] = line[3];
		l2[1] = line[4];
		l2[2] = line[5];
		
		float[] plane = getPlane(points);
		float[] lineVector = subtract(l2, l1);
		float parameter = solveForParam(plane, l1, lineVector);
		float[] point = getPoint(parameter, l1, lineVector);
		if (Math.abs(linearSolve(parameter, plane, point)) <= MARGIN_OF_ERROR) {
			return point;
		}
		return null;
	}
	
	public static float solveForParam(float[] plane, float[] origin, float[] lineVector) {
		// sum our non variable numbers
		float nonParamSum = 0;
		for (int i = 0; i < 3; i++) {
			nonParamSum += plane[i] * origin[i];
		}
		nonParamSum += plane[3];
		
		// sum our coefficients
		float paramCoef = 0;
		for (int i = 0; i < 3; i++) {
			paramCoef += plane[i] * lineVector[i];
		}
		// not safe!
		return - nonParamSum / paramCoef;
	}
	
	public static float[] getPoint(float parameter, float[] origin, float[] lineVector) {
		float[] result = new float[3];
		for (int i = 0; i < 3; i++) {
			result[i] = origin[i] + parameter * lineVector[i];
		}
		return result;
	}
	
	public static float linearSolve(float parameter, float[] plane, float[] point) {
		float result = 0;
		for (int i = 0; i < 3; i ++) {
			result += plane[i] * point[i];
		}
		result += plane[3];
		return result;
	}
	
	public static float[] product(float k, float[] v) {
		float[] result = new float[v.length];
		for (int i = 0; i < v.length; i++) {
			result[i] = k * v[i];
		}
		return result;
	}
	
	public static float[] getPlane(float[] threePoints) {
		float[] result = new float[4];
		float[] p1 = new float[3];
		float[] p2 = new float[3];
		float[] p3 = new float[3];
		
		p1[0] = threePoints[0];
		p1[1] = threePoints[1];
		p1[2] = threePoints[2];
		
		p2[0] = threePoints[3];
		p2[1] = threePoints[4];
		p2[2] = threePoints[5];
		

		p3[0] = threePoints[6];
		p3[1] = threePoints[7];
		p3[2] = threePoints[8];
		
		float[] p21 = subtract(p2, p1);
		float[] p31 = subtract(p3, p1);
		
		float[] normal = crossProduct(p21, p31);
		result[0] = normal[0];
		result[1] = normal[1];
		result[2] = normal[2];
		result[3] = -dotProduct(normal, p1);
		return result;
	}
	
	public static float[] crossProduct(float[] abc, float[] xyz) {
		float[] result = new float[3];
		
		result[0] = abc[1]*xyz[2] - abc[2]*xyz[1];
		result[1] = abc[2]*xyz[0] - abc[0]*xyz[2];
		result[2] = abc[0]*xyz[1] - abc[1]*xyz[0];
		
		return result;
	}
	
	public static float dotProduct(float[] a, float[] b) {
		float result = 0;
		for (int i = 0; i < a.length; i++) {
			result += a[i]*b[i];
		}
		return result;
	}
	public static float[] add(float[] a, float[]b) {
		float[] result = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			result[i] = a[i]+b[i];
		}
		return result;
	}
	public static float[] subtract(float[] a, float[]b) {
		float[] result = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			result[i] = a[i]-b[i];
		}
		return result;
	}
	
	public static void testPlane(float[] points) {
		float[] plane = getPlane(points);
		String eq = String.format("%fx+%fy+%fz+%f=0", plane[0], plane[1], plane[2], plane[3]);
		System.out.println(eq);
	}
	
	public static void testGetIntersection(float[] points, float[] line) {
		float[] point = getIntersection(points, line);
		if (point == null) {
			System.out.println("No intersection!");
			return;
		}
		
		String str = String.format("Intersection at (%f, %f, %f)", point[0], point[1], point[2]);
		System.out.println(str);
	}
	
	public static void main(String[] args) {
		float[] points1 = {1,2,3,4,6,9,12,11,9};
		testPlane(points1);
		
		float[] points2 = {1,0,0,0,1,0,1,1,0};
		float [] line2 = {1,1,1,1,1,-1};
		testPlane(points2);
		testGetIntersection(points2, line2);
	}
}
