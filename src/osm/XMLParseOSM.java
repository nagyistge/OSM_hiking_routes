/* 
 * Copyright (C) 2016 Mátyás Gede <saman at map.elte.hu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package osm;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeSet;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Simple XML element class; knows its name, attributes, children, parent */
class XMLElement {
    String name;
    /** level: current level. Level 0 has no parent. */
    int level; 
    String[] attrNames,attrValues;
    XMLElement parent;
    ArrayList<XMLElement> children=new ArrayList();
    
    XMLElement(XMLElement p, String n, String[] an, String[] av) {
        parent=p;
        if (p==null)
            level=0;
        else {
            level=p.level+1;
            parent.children.add(this);
        }
        name=n;attrNames=an;attrValues=av;
    }
    
    /**
     * retrieves the value of an attribute
     * @param an name of the attribute to get
     * @return the value of 'an'
     */
    String getAttrValue(String an) {
        for(int i=0;i<attrNames.length;i++)
            if (attrNames[i].equals(an))
                return attrValues[i];
        return null;
    }
    
    /** recursively lists the element */
    void explain() {
        for(int i=0;i<level;i++)
            System.out.print(" ");
        System.out.print(name+":");
        for(int i=0;i<attrNames.length;i++)
            System.out.print(" "+attrNames[i]+"="+attrValues[i]);
        System.out.println();
        for(int i=0;i<children.size();i++)
            children.get(i).explain();
    }
}

/**
 * Extract marked paths from OSM XML - currently using Hungarian "jel"=* tags
 * @author saman
 */
public class XMLParseOSM {

    /**
     * Reads the next XMLElement with a specified name from an XMLStreamReader
     * @param xsr source XMLStreamReader
     * @param eName element name
     * @return
     * @throws XMLStreamException 
     */
    static XMLElement nextElement(XMLStreamReader xsr, String eName) throws XMLStreamException {
        XMLElement aElem=null;
        while (xsr.hasNext()) {
            xsr.next();
            if(xsr.getEventType()==XMLStreamReader.START_ELEMENT&&(aElem!=null||xsr.getLocalName().equals(eName))) {
                String aName=xsr.getLocalName();
                int ac=xsr.getAttributeCount();
                String[] an=new String[ac];
                String[] av=new String[ac];
                for(int i=0;i<ac;i++) {
                    an[i]=xsr.getAttributeLocalName(i);
                    av[i]=xsr.getAttributeValue(i);
                }
                aElem=new XMLElement(aElem,aName,an,av);
            }
            if(xsr.getEventType()==XMLStreamReader.END_ELEMENT&&aElem!=null) {
                if (aElem.parent==null) {
                    return aElem;
                }
                aElem=aElem.parent;
            }
        }
        return null; // returns null if the stream ended unexpectedly
    } // end of nextElement
        
    /**
     * @param args the command line arguments
     * @throws javax.xml.stream.XMLStreamException
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws XMLStreamException, FileNotFoundException {
        
        Scanner kin=new Scanner(System.in);
        System.out.print("OSM file to read: ");
        String fn=kin.nextLine(); //"d:/osm/v_12.osm";
        System.out.print("Output filename 1: ");
        String ofn=kin.nextLine(); //"d:/osm/v_12.kml";
        System.out.print("Output filename 2: ");
        String ofn2=kin.nextLine(); //"d:/osm/v_12_2.kml";
        
        // start watch
        long startTime = System.currentTimeMillis();
        
        XMLInputFactory xmlIF = XMLInputFactory.newInstance();
        XMLStreamReader xmlSR = xmlIF.createXMLStreamReader(new FileInputStream(fn));
        // array of way IDs stored for each relation
        ArrayList<ArrayList<Long>> wayList = new ArrayList();
        // a set containing all way IDs involved in marked paths
        TreeSet<Long> waySet = new TreeSet();
        // list of "jel" tag values from relations
        // elements of jelList and wayList mean the same relation
        ArrayList<String> jelList = new ArrayList();
        int cnt=0;
        XMLElement aElem;
        // first round: looking for relation tags
        while (xmlSR.hasNext()) {
            aElem=nextElement(xmlSR,"relation");
            if (aElem!=null) {
                // find "jel" tag
                for(int i=0;i<aElem.children.size();i++)
                    if(aElem.children.get(i).name.equals("tag")&&aElem.children.get(i).getAttrValue("k").equals("jel")) {
                        jelList.add(aElem.children.get(i).getAttrValue("v"));
                        // store way IDs in this relation
                        ArrayList<Long> wl=new ArrayList();
                        for(int j=0;j<aElem.children.size();j++)
                            if (aElem.children.get(j).name.equals("member")&&aElem.children.get(j).getAttrValue("type").equals("way")) {
                                Long wId=Long.parseLong(aElem.children.get(j).getAttrValue("ref"));
                                wl.add(wId);
                                waySet.add(wId);
                            }
                        wayList.add(wl);
                        //aElem.explain();
                        cnt++;
                        break;
                    }                
            }
        }
        System.out.println(wayList.size()+" marked path relation found.");
        System.out.println("time: "+(System.currentTimeMillis()-startTime)+" ms.");
        xmlSR.close();
        
        xmlSR = xmlIF.createXMLStreamReader(new FileInputStream(fn));
        
        // second round: looking for relevant ways
        // list of nodes for each way
        ArrayList<ArrayList<Long>> nodeList=new ArrayList();
        // a set containing all node IDs involved in marked paths
        TreeSet<Long> nodeSet=new TreeSet();
        ArrayList<Long> wayIds=new ArrayList();
        while (xmlSR.hasNext()) {
            aElem=nextElement(xmlSR,"way");
            if (aElem!=null) {
                // search its id in way lists
                Long aId=Long.parseLong(aElem.getAttrValue("id"));
                //for(int i=0;i<wayList.size();i++)
                    //if (wayList.get(i).indexOf(aId)>-1) {
                    if (waySet.contains(aId)) {
                        // found the id on a waylist: store its nodes and id
                        wayIds.add(aId);
                        ArrayList<Long> nl=new ArrayList();
                        for(int j=0;j<aElem.children.size();j++)
                            if (aElem.children.get(j).name.equals("nd")) {
                                Long nId=Long.parseLong(aElem.children.get(j).getAttrValue("ref"));
                                nl.add(nId);
                                nodeSet.add(nId);
                            }
                        nodeList.add(nl);
                        //break;
                    }
            }
        }
        System.out.println(nodeList.size()+" way found, with altogether "+nodeSet.size()+" node IDs.");
        System.out.println("time: "+(System.currentTimeMillis()-startTime)+" ms.");
        
        xmlSR.close();
        xmlSR = xmlIF.createXMLStreamReader(new FileInputStream(fn));
        
        // third round: looking for relevant nodes
        ArrayList<Long> nodeIds=new ArrayList();
        ArrayList<String> nodeLons=new ArrayList();
        ArrayList<String> nodeLats=new ArrayList();
        while (xmlSR.hasNext()) {
            aElem=nextElement(xmlSR,"node");
            if (aElem!=null) {
                // search its id in node lists
                Long aId=Long.parseLong(aElem.getAttrValue("id"));
                if (nodeSet.contains(aId)) {    
                    // found the id on the node set: store its id,lat,lon
                    nodeIds.add(aId);
                    nodeLons.add(aElem.getAttrValue("lon"));
                    nodeLats.add(aElem.getAttrValue("lat"));
                }
            }
        }
        System.out.println(nodeIds.size()+" relevant nodes stored");
        System.out.println("time: "+(System.currentTimeMillis()-startTime)+" ms.");
        
        // generating KML from data
        PrintStream kml=new PrintStream(ofn);
        kml.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        kml.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        kml.println("<Document>");
        String jelek,coords,desc;
        String tjBaseUrl="tj/"; // "http://mercator.elte.hu/~saman/hu/okt/mapserver/turistajelzesek/";
        Long aWid,aNid;
        for(int i=0;i<wayIds.size();i++) {
            jelek="";desc="";
            aWid=wayIds.get(i);
            for(int j=0;j<wayList.size();j++)
                if (wayList.get(j).indexOf(aWid)>-1) {
                    String aJel=jelList.get(j);
                    if (!jelek.equals(aJel)&&!jelek.startsWith(aJel+",")&&!jelek.endsWith(","+aJel)&&!jelek.contains(","+aJel+",")) {
                        jelek+=(jelek.length()>0?",":"")+aJel;
                        desc+="<img src=\""+tjBaseUrl+aJel+".png\" /> ";
                    }
                }
            coords="";
            for(int j=0;j<nodeList.get(i).size();j++) {
                aNid=nodeList.get(i).get(j);
                int nn=nodeIds.indexOf(aNid);
                coords+=nodeLons.get(nn)+","+nodeLats.get(nn)+" ";
            }
            kml.println("<Placemark><name>"+jelek+"</name><description><![CDATA["+desc+"]]></description>");
            kml.println("<LineString><coordinates>"+coords+"</coordinates></LineString>");
            kml.println("</Placemark>");
        }
        kml.println("</Document>");
        kml.println("</kml>");

        // generating another KML from data
        kml=new PrintStream(ofn2);
        kml.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        kml.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        kml.println("<Document>");
        for(int i=0;i<wayIds.size();i++) {
            jelek="";desc="";
            aWid=wayIds.get(i);
            coords="";
            for(int j=0;j<nodeList.get(i).size();j++) {
                aNid=nodeList.get(i).get(j);
                int nn=nodeIds.indexOf(aNid);
                coords+=nodeLons.get(nn)+","+nodeLats.get(nn)+" ";
            }
            for(int j=0;j<wayList.size();j++)
                if (wayList.get(j).indexOf(aWid)>-1) {
                    String aJel=jelList.get(j);
                    desc="<img src=\""+tjBaseUrl+aJel+".png\" /> ";
                    kml.println("<Placemark><name>"+aJel+"</name><description><![CDATA["+desc+"]]></description>");
                    kml.println("<LineString><coordinates>"+coords+"</coordinates></LineString>");
                    kml.println("</Placemark>");                   
                }
        }
        kml.println("</Document>");
        kml.println("</kml>");        
        System.out.println("Run time: "+(System.currentTimeMillis()-startTime)+" ms.");
    } // end of main
    
}
